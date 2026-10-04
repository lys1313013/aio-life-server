package top.aiolife.sso.wechat;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接本地测试 Redis；验证真实 Lua 的竞争、过期和凭证隔离。 */
class WechatWebTicketStoreIntegrationTest {
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    WechatWebTicketStore store;
    String prefix;
    String scene;
    String secret;

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.getInteger("wechat.test.redis.port", 6379));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
    }
    @AfterAll static void close() { connection.destroy(); }
    @BeforeEach void setup() {
        var properties = new WechatMiniProperties();
        properties.setAppId("web_test_" + UUID.randomUUID());
        prefix = "auth:wechat:web:" + properties.getAppId() + ":";
        store = new WechatWebTicketStore(redis, properties);
        scene = WechatWebTicketStore.random(16);
        secret = WechatWebTicketStore.random(32);
        store.create(scene, secret);
    }
    @AfterEach void cleanup() { redis.delete(prefix + scene); }

    @Test void 扫码只标记状态且必须明确确认才能兑换() {
        assertEquals("WAITING", store.browser(scene, secret, "exchange"));
        assertEquals("WAITING", store.mobile(scene, 13, "confirm"));
        assertEquals("SCANNED", store.mobile(scene, 13, "scan"));
        assertEquals("SCANNED", store.browser(scene, secret, "exchange"));
        assertEquals("CONFIRMED", store.mobile(scene, 13, "confirm"));
        assertEquals("USER:13", store.browser(scene, secret, "exchange"));
        assertEquals("CONSUMED", store.browser(scene, secret, "exchange"));
        assertEquals("CONSUMED", store.mobile(scene, 13, "confirm"));
    }

    @Test void 二维码及其他浏览器密钥不能查询取消或兑换() {
        for (String op : new String[]{"status", "exchange", "revoke"}) {
            assertThrows(ResponseStatusException.class, () -> store.browser(scene, WechatWebTicketStore.random(32), op));
            assertThrows(ResponseStatusException.class, () -> store.browser(scene, scene, op));
        }
        assertEquals("WAITING", store.browser(scene, secret, "status"));
        assertNotEquals(secret, redis.opsForHash().get(prefix + scene, "secret"));
    }

    @Test void 大整数用户ID不会经过Lua浮点数且其他账号不能抢确认() {
        long uid = 9223372036854775806L;
        assertEquals("SCANNED", store.mobile(scene, uid, "scan"));
        for (String op : new String[]{"scan", "confirm", "cancel"}) {
            assertThrows(ResponseStatusException.class, () -> store.mobile(scene, 22, op));
        }
        assertEquals("CONFIRMED", store.mobile(scene, uid, "confirm"));
        assertEquals("USER:" + uid, store.browser(scene, secret, "exchange"));
    }

    @Test void 并发兑换只成功一次() throws Exception {
        store.mobile(scene, 13, "scan");
        store.mobile(scene, 13, "confirm");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 8).<Callable<String>>mapToObj(i ->
                    () -> store.browser(scene, secret, "exchange")).toList();
            int successes = 0;
            for (var result : executor.invokeAll(calls)) if (result.get().equals("USER:13")) successes++;
            assertEquals(1, successes);
        }
    }

    @Test void 取消及网页撤销都不可恢复为确认() {
        store.mobile(scene, 13, "scan");
        assertEquals("CANCELLED", store.mobile(scene, 13, "cancel"));
        assertEquals("CANCELLED", store.mobile(scene, 13, "confirm"));
        assertEquals("CANCELLED", store.browser(scene, secret, "exchange"));
    }

    @Test void 已确认但未兑换时浏览器可撤销() {
        store.mobile(scene, 13, "scan");
        store.mobile(scene, 13, "confirm");
        store.browser(scene, secret, "revoke");
        assertEquals("CANCELLED", store.browser(scene, secret, "exchange"));
    }

    @Test void 状态变化不续期且过期无法复活() throws Exception {
        redis.expire(prefix + scene, Duration.ofMillis(100));
        store.mobile(scene, 13, "scan");
        assertTrue(redis.getExpire(prefix + scene, TimeUnit.MILLISECONDS) <= 100);
        Thread.sleep(150);
        assertEquals("EXPIRED", store.mobile(scene, 13, "confirm"));
        assertEquals("EXPIRED", store.browser(scene, secret, "exchange"));
    }
}
