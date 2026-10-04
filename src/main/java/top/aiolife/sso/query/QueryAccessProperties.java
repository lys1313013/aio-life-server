package top.aiolife.sso.query;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 默认关闭；内部服务密钥只由部署环境提供。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aio.life.query-access")
public class QueryAccessProperties implements InitializingBean {
    private boolean enabled;
    private String serviceKey = "";
    private Set<String> allowedApps = Set.of("aio-query");

    @Override
    public void afterPropertiesSet() {
        if (enabled && (serviceKey == null || serviceKey.length() < 32)) {
            throw new IllegalStateException("启用查询授权必须配置至少 32 字符的内部服务密钥");
        }
    }
}
