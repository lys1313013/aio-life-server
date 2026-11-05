package top.aiolife.record.config;

import java.net.URI;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 查询入口仅由部署环境配置，目标本身不可指定 URL 或身份。 */
@Data
@Component
@ConfigurationProperties(prefix = "aio.life.server.goal-progress")
public class GoalProgressProperties {
    private URI queryUrl = URI.create("http://127.0.0.1:45680/graphql");
}
