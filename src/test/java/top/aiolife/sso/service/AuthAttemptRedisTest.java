package top.aiolife.sso.service;

import cn.hutool.crypto.SecureUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.sso.config.AuthAbuseProperties;
import top.aiolife.sso.service.AuthAttemptStore.EmailPurpose;

import static org.junit.jupiter.api.Assertions.*;

/** 通过独立容器验证 Redis 脚本，使用两个存储对象模拟跨实例调用。 */
class AuthAttemptRedisTest {
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    private static final String IP = "192.0.2.1";
    private static final String EMAIL = "Owner@example.test";
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;

    private AuthAbuseProperties limits;
    private AuthAttemptStore first;
    private AuthAttemptStore second;

    @BeforeAll
    static void connect() {
        REDIS.start();
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
    }

    @AfterAll
    static void close() {
        if (connection != null) connection.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void initialize() {
        redis.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });
        limits = new AuthAbuseProperties();
        first = new AuthAttemptStore(redis, limits);
        second = new AuthAttemptStore(redis, limits);
    }

    @Test
    void testLoginReserve_并行请求只放行组合额度且账号大小写共用() throws Exception {
        var outcomes = race(32, i -> {
            try {
                instance(i).reserveLogin(i % 2 == 0 ? "OperatorΣ" : "OPERATORσ", IP);
                return true;
            } catch (ResponseStatusException e) {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
                return false;
            }
        });
        assertEquals(5, outcomes.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void testLoginReserve_不同来源共享账号额度且同源不同账号共享请求额度() {
        limits.getLogin().setAccountLimit(2);
        limits.getLogin().setIpLimit(2);
        var successful = first.reserveLogin("user", IP);
        second.reserveLogin("USER", "192.0.2.2");
        var throttled = assertLimited(() -> second.reserveLogin("User", "192.0.2.3"));
        var response = new ExceptionHandle().handleResponseStatus(throttled);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(throttled.getReason(), response.getBody().getMessage());
        assertTrue(Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)) <= 900);

        first.releaseSuccessfulLogin(successful);
        second.reserveLogin("user", "192.0.2.3");
        assertLimited(() -> second.reserveLogin("user", "192.0.2.4"));
        first.reserveLogin("other", IP);
        assertLimited(() -> second.reserveLogin("third", IP));
    }

    @Test
    void testLoginRelease_重复和跨窗口释放不会清除其他请求的失败记录() {
        limits.getLogin().setAccountIpLimit(1);
        var old = first.reserveLogin("user", IP);
        redis.expire(old.accountKey(), Duration.ZERO);
        redis.expire(old.accountIpKey(), Duration.ZERO);
        var current = second.reserveLogin("user", IP);
        first.releaseSuccessfulLogin(old);
        first.releaseSuccessfulLogin(old);
        assertLimited(() -> second.reserveLogin("user", IP));

        first.releaseSuccessfulLogin(current);
        var later = second.reserveLogin("user", IP);
        first.releaseSuccessfulLogin(current);
        assertLimited(() -> second.reserveLogin("user", IP));
        assertEquals(1L, redis.opsForSet().size(later.accountIpKey()));
    }

    @Test
    void testEmailIssue_大小写变体和不同用途并发只发放一次() throws Exception {
        var outcomes = race(30, i -> {
            try {
                instance(i).issueEmailCode(EmailPurpose.values()[i % 3],
                        i % 2 == 0 ? EMAIL : "OWNER@EXAMPLE.TEST", "192.0.2." + i);
                return true;
            } catch (ResponseStatusException e) {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
                return false;
            }
        });
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void testEmailIssue_三个日配额按发送一次扣减且拒绝不占用其他额度() {
        limits.getCode().setIpLimitDay(1);
        limits.getCode().setEmailLimitDay(1);
        limits.getCode().setTotalLimitDay(2);
        first.issueEmailCode(EmailPurpose.REGISTER, EMAIL, IP);
        redis.expire("auth:{email-code}:cooldown:" + SecureUtil.sha256("owner@example.test"), Duration.ZERO);
        assertLimited(() -> second.issueEmailCode(EmailPurpose.RESET_PASSWORD, "owner@example.test", "192.0.2.2"));
        assertLimited(() -> second.issueEmailCode(EmailPurpose.RESET_PASSWORD, "second@example.test", IP));
        second.issueEmailCode(EmailPurpose.RESET_PASSWORD, "second@example.test", "192.0.2.2");
        assertLimited(() -> first.issueEmailCode(EmailPurpose.RESET_SECONDARY_PASSWORD, "third@example.test", "192.0.2.3"));
    }

    @Test
    void testEmailConsume_跨实例只可成功一次且用途和收件地址隔离() throws Exception {
        String code = first.issueEmailCode(EmailPurpose.REGISTER, EMAIL, IP);
        assertTrue(code.matches("[0-9]{6}"));
        assertThrows(IllegalArgumentException.class, () -> second.consumeEmailCode(EmailPurpose.RESET_PASSWORD, EMAIL, code));
        assertThrows(IllegalArgumentException.class, () -> second.consumeEmailCode(EmailPurpose.REGISTER, "owner@example.test", code));
        var outcomes = race(24, i -> {
            try {
                instance(i).consumeEmailCode(EmailPurpose.REGISTER, EMAIL, code);
                return true;
            } catch (IllegalArgumentException e) {
                assertEquals("验证码错误或已过期", e.getMessage());
                return false;
            }
        });
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void testEmailConsume_重新发送重置尝试次数且五次错误后立即作废() {
        String codeKey = "auth:{email-code}:value:REGISTER:" + SecureUtil.sha256("owner@example.test");
        first.issueEmailCode(EmailPurpose.REGISTER, EMAIL, IP);
        for (int i = 0; i < 4; i++) {
            assertThrows(IllegalArgumentException.class, () -> first.consumeEmailCode(EmailPurpose.REGISTER, EMAIL, "wrong"));
        }
        redis.expire("auth:{email-code}:cooldown:" + SecureUtil.sha256("owner@example.test"), Duration.ZERO);
        String code = second.issueEmailCode(EmailPurpose.REGISTER, EMAIL, IP);
        long ttl = redis.getExpire(codeKey, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 4; i++) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> first.consumeEmailCode(EmailPurpose.REGISTER, EMAIL, "wrong"));
            assertEquals("验证码错误或已过期", failure.getMessage());
        }
        assertTrue(redis.getExpire(codeKey, TimeUnit.MILLISECONDS) <= ttl);
        var fifth = assertThrows(IllegalArgumentException.class,
                () -> second.consumeEmailCode(EmailPurpose.REGISTER, EMAIL, "wrong"));
        assertTrue(fifth.getMessage().contains("错误次数过多"));
        assertFalse(redis.hasKey(codeKey));
        assertThrows(IllegalArgumentException.class, () -> second.consumeEmailCode(EmailPurpose.REGISTER, EMAIL, code));
    }

    private AuthAttemptStore instance(int index) {
        return index % 2 == 0 ? first : second;
    }

    private static ResponseStatusException assertLimited(Runnable operation) {
        var error = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertTrue(Long.parseLong(error.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)) > 0);
        return error;
    }

    private static List<Boolean> race(int count, IntFunction<Boolean> operation) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                tasks.add(pool.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return operation.apply(index);
                }));
            }
            start.countDown();
            List<Boolean> outcomes = new ArrayList<>();
            for (var task : tasks) {
                outcomes.add(task.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }
}
