package top.aiolife.record.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 豆瓣封面仅允许配置中的域名及其子域名，不接受任意远程图片地址。
 */
@Component
public class DoubanCoverUrlPolicy {

    private final Set<String> allowedDomains;

    public DoubanCoverUrlPolicy(
            @Value("${aio.life.server.douban.cover-allowed-domains:}") String allowedDomains) {
        this.allowedDomains = Arrays.stream(allowedDomains.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String validate(String imageUrl) {
        try {
            URI uri = new URI(imageUrl == null ? "" : imageUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !isAllowedHost(uri.getHost())) {
                throw rejected();
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw rejected();
        }
    }

    private boolean isAllowedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return allowedDomains.stream().anyMatch(domain ->
                normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
    }

    private IllegalArgumentException rejected() {
        return new IllegalArgumentException("封面链接仅支持受信任的豆瓣图片来源，自定义封面请上传图片");
    }
}
