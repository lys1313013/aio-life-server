package top.aiolife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedissonConfigTest {
    private final RedissonConfig config = new RedissonConfig();

    @Test
    void createConfig_复用连接认证数据库和超时() {
        var details = mock(RedisConnectionDetails.class);
        var standalone = mock(RedisConnectionDetails.Standalone.class);
        when(details.getStandalone()).thenReturn(standalone);
        when(standalone.getHost()).thenReturn("redis.internal");
        when(standalone.getPort()).thenReturn(6380);
        when(standalone.getDatabase()).thenReturn(3);
        when(details.getUsername()).thenReturn("app");
        when(details.getPassword()).thenReturn("password");
        var properties = new RedisProperties();
        properties.getSsl().setEnabled(true);
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setConnectTimeout(Duration.ofSeconds(4));
        var server = config.createConfig(details, properties).useSingleServer();
        assertEquals("rediss://redis.internal:6380", server.getAddress());
        assertEquals(3, server.getDatabase());
        assertEquals("app", server.getUsername());
        assertEquals("password", server.getPassword());
        assertEquals(2000, server.getTimeout());
        assertEquals(4000, server.getConnectTimeout());
    }

    @Test
    void createConfig_支持IPv6且空密码不启用认证() {
        var details = mock(RedisConnectionDetails.class);
        var standalone = mock(RedisConnectionDetails.Standalone.class);
        when(details.getStandalone()).thenReturn(standalone);
        when(standalone.getHost()).thenReturn("::1");
        when(standalone.getPort()).thenReturn(6379);
        when(details.getPassword()).thenReturn("");
        var server = config.createConfig(details, new RedisProperties()).useSingleServer();
        assertEquals("redis://[::1]:6379", server.getAddress());
        assertNull(server.getPassword());
    }

    @Test
    void createConfig_集群配置不能静默连接默认单节点() {
        var details = mock(RedisConnectionDetails.class);
        when(details.getCluster()).thenReturn(mock(RedisConnectionDetails.Cluster.class));
        assertThrows(IllegalStateException.class, () -> config.createConfig(details, new RedisProperties()));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AIO_LOCK_TEST_REDIS_PORT", matches = ".+")
    void redissonClient_自动配置共存且保留Lettuce连接() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class))
                .withUserConfiguration(RedissonConfig.class)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + System.getenv("AIO_LOCK_TEST_REDIS_PORT"),
                        "spring.data.redis.database=2")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class,
                            context.getBean(org.springframework.data.redis.connection.RedisConnectionFactory.class));
                    var client = context.getBean(org.redisson.api.RedissonClient.class);
                    assertEquals(2, client.getConfig().useSingleServer().getDatabase());
                    assertTrue(client.getLock("test:config").tryLock());
                    client.getLock("test:config").unlock();
                });
    }
    @Test
    void createConfig_rediss连接URL不能降级为明文() {
        var details = mock(RedisConnectionDetails.class);
        var standalone = mock(RedisConnectionDetails.Standalone.class);
        when(details.getStandalone()).thenReturn(standalone);
        when(standalone.getHost()).thenReturn("redis.internal");
        when(standalone.getPort()).thenReturn(6380);
        var properties = new RedisProperties();
        properties.setUrl("rediss://redis.internal:6380");
        assertEquals("rediss://redis.internal:6380", config.createConfig(details, properties).useSingleServer().getAddress());
    }
}
