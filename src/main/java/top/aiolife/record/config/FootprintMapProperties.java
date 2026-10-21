package top.aiolife.record.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.server.footprints")
public class FootprintMapProperties {
    private String baiduServiceAk = "";
    private String baiduBrowserAk = "";
    private int timeoutMs = 5000;
}
