package top.aiolife.record.prediction;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 推荐链路数据库查询缓存配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.server.recommendation-cache")
public class RecommendationCacheProperties {
    private long ttlMs = 18_000_000L;
}
