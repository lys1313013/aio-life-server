package top.aiolife.sso.wechat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 单个微信小程序配置，凭据仅由服务端环境变量注入。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.auth.wechat-mini")
public class WechatMiniProperties {
    private boolean enabled;
    private String appId = "";
    private String appSecret = "";
}
