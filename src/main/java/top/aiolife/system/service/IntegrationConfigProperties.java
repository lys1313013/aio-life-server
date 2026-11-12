package top.aiolife.system.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 系统配置首次保存前的部署默认值。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.server")
public class IntegrationConfigProperties {
    private Mbti mbti = new Mbti();

    @Getter
    @Setter
    public static class Mbti {
        private String apiKey = "";
        private String baseUrl = "https://api.devil.ai";
    }
}
