package top.aiolife.sso.wechat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 微信环境变量默认值；保存系统配置后由 WechatSystemConfigService 提供运行时配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.auth.wechat-mini")
public class WechatMiniProperties {
    private boolean enabled;
    // 确认页正式发布后再开启网页扫码入口。
    private boolean webScanEnabled;
    private String webScanEnvVersion = "release";
    private String appId = "";
    private String appSecret = "";
}
