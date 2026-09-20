package top.aiolife.record.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.autotask.NotificationRetryTask;
import top.aiolife.record.service.FeishuNotificationService;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 显式连接临时 Redis，使用两个独立客户端模拟两个应用实例。 */
@EnabledIfEnvironmentVariable(named = "AIO_LOCK_TEST_REDIS_PORT", matches = ".+")
class RedissonNotificationIntegrationTest {
    private static RedissonClient first;
    private static RedissonClient second;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisUtil redis;

    @BeforeAll
    static void setup() {
        int port = Integer.parseInt(System.getenv("AIO_LOCK_TEST_REDIS_PORT"));
        first = client(port);
        second = client(port);
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new RedisUtil(new StringRedisTemplate(connectionFactory), new ObjectMapper());
    }

    private static RedissonClient client(int port) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setLockWatchdogTimeout(1500);
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(4)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2);
        return Redisson.create(config);
    }

    @AfterAll
    static void cleanup() {
        if (first != null) first.shutdown();
        if (second != null) second.shutdown();
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void sendOnce_两个实例并发及晚到请求同事件只发一次() throws Exception {
        var sender = mock(AbstractNotificationSender.class);
        when(sender.getChannel()).thenReturn("EMAIL");
        when(sender.send(any(), any(), any(), any())).thenReturn(true);
        var user = new UserEntity();
        user.setId(7L);
        var request = request(UUID.randomUUID() + ":2026-09-20");
        var firstGuard = new NotificationSendGuard(new DistributedLockExecutor(first), redis);
        var secondGuard = new NotificationSendGuard(new DistributedLockExecutor(second), redis);
        runConcurrent(16, index -> (index % 2 == 0 ? firstGuard : secondGuard).sendOnce(sender, user, request, "html"));
        secondGuard.sendOnce(sender, user, request, "html");
        verify(sender, times(1)).send(any(), any(), any(), any());
        // 同用户次日事件仍然正常发送。
        secondGuard.sendOnce(sender, user, request(request.dedupKey() + ":next-day"), "html");
        verify(sender, times(2)).send(any(), any(), any(), any());
        // 其他渠道不能被邮件成功标记屏蔽。
        var station = mock(AbstractNotificationSender.class);
        when(station.getChannel()).thenReturn("STATION");
        when(station.send(any(), any(), any(), any())).thenReturn(true);
        secondGuard.sendOnce(station, user, request, "html");
        verify(station).send(any(), any(), any(), any());
    }

    @Test
    void sendOnce_失败不标记成功且后续触发可以重试() {
        var sender = mock(AbstractNotificationSender.class);
        when(sender.getChannel()).thenReturn("EMAIL");
        when(sender.send(any(), any(), any(), any())).thenReturn(false, true);
        var user = new UserEntity();
        user.setId(7L);
        var request = request(UUID.randomUUID().toString());
        new NotificationSendGuard(new DistributedLockExecutor(first), redis).sendOnce(sender, user, request, "html");
        var guard = new NotificationSendGuard(new DistributedLockExecutor(second), redis);
        guard.sendOnce(sender, user, request, "html");
        guard.sendOnce(sender, user, request, "html");
        verify(sender, times(2)).send(any(), any(), any(), any());
    }

    @Test
    void tryRun_watchdog在初始租期后仍保持互斥并在完成后释放() throws Exception {
        String key = "test:watchdog:" + UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = pool.submit(() -> new DistributedLockExecutor(first).tryRun(key, () -> {
                entered.countDown();
                await(release);
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                Thread.sleep(2500); // 大于测试 watchdog 的初始 1500ms，验证真实 Redis 续期。
                assertFalse(new DistributedLockExecutor(second).tryRun(key, () -> fail("锁提前过期")));
            } finally {
                release.countDown();
            }
            assertTrue(worker.get(5, TimeUnit.SECONDS));
            assertTrue(new DistributedLockExecutor(second).tryRun(key, () -> {}));
        }
    }

    @Test
    void retryTask_竞争失败跳过且原实例失败后可恢复() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstService = mock(FeishuNotificationService.class);
        var secondService = mock(FeishuNotificationService.class);
        doAnswer(invocation -> { entered.countDown(); await(release); throw new IllegalStateException("retry failure"); })
                .when(firstService).retryPending();
        var task1 = new NotificationRetryTask(firstService, new DistributedLockExecutor(first));
        var task2 = new NotificationRetryTask(secondService, new DistributedLockExecutor(second));
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = pool.submit(task1::retryFeishuNotifications);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                task2.retryFeishuNotifications();
                verifyNoInteractions(secondService);
            } finally {
                release.countDown();
            }
            assertThrows(ExecutionException.class, () -> worker.get(5, TimeUnit.SECONDS));
            task2.retryFeishuNotifications();
            verify(secondService).retryPending();
        }
    }

    private NotificationRequest request(String key) {
        return new NotificationRequest(7L, "LEETCODE_REMINDER", "title", "text", null, key);
    }

    static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static void runConcurrent(int count, java.util.function.IntConsumer action) throws Exception {
        try (var pool = Executors.newFixedThreadPool(count)) {
            var ready = new CountDownLatch(count);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> { ready.countDown(); await(start); action.accept(index); }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
        }
    }
}
