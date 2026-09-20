package top.aiolife.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

/** 分布式锁使用独立 Redisson 客户端，复用现有单节点 Redis 配置。 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisConnectionDetails connection, RedisProperties properties) {
        return Redisson.create(createConfig(connection, properties));
    }

    Config createConfig(RedisConnectionDetails connection, RedisProperties properties) {
        if (connection.getCluster() != null || connection.getSentinel() != null) {
            throw new IllegalStateException("Redisson 锁当前使用单节点 Redis，请为集群或哨兵部署配置对应模式");
        }
        RedisConnectionDetails.Standalone standalone = connection.getStandalone();
        String host = standalone.getHost();
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        boolean ssl = properties.getSsl().isEnabled()
                || (StringUtils.hasText(properties.getUrl())
                && "rediss".equalsIgnoreCase(URI.create(properties.getUrl()).getScheme()));
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        SingleServerConfig server = config.useSingleServer()
                .setAddress((ssl ? "rediss://" : "redis://")
                        + host + ":" + standalone.getPort())
                .setDatabase(standalone.getDatabase())
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        if (StringUtils.hasText(connection.getUsername())) {
            server.setUsername(connection.getUsername());
        }
        if (StringUtils.hasText(connection.getPassword())) {
            server.setPassword(connection.getPassword());
        }
        if (properties.getTimeout() != null) {
            server.setTimeout(Math.toIntExact(properties.getTimeout().toMillis()));
        }
        if (properties.getConnectTimeout() != null) {
            server.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        }
        return config;
    }
}
