package top.aiolife.sso.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.util.RedisUtil;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 微信服务端凭证交换，不向调用方透传微信响应、session_key 或包含密钥的异常。 */
@Component
public class WechatMiniClient {
    private final WechatMiniProperties properties;
    private final RedisUtil redis;
    private final DistributedLockExecutor locks;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public WechatMiniClient(WechatMiniProperties properties, RedisUtil redis, DistributedLockExecutor locks) {
        this(properties, redis, locks, builder());
    }

    WechatMiniClient(WechatMiniProperties properties, RedisUtil redis, DistributedLockExecutor locks,
                     RestClient.Builder builder) {
        this.properties = properties;
        this.redis = redis;
        this.locks = locks;
        this.client = builder.baseUrl("https://api.weixin.qq.com").build();
    }

    private static RestClient.Builder builder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory);
    }

    public boolean enabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getAppId())
                && StringUtils.hasText(properties.getAppSecret());
    }

    public void requireEnabled() {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂未开通，请使用账号密码登录");
    }

    public Identity exchangeLogin(String code) {
        requireEnabled();
        JsonNode body;
        try {
            body = client.get().uri(uri -> uri.path("/sns/jscode2session")
                    .queryParam("appid", properties.getAppId()).queryParam("secret", properties.getAppSecret())
                    .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw unavailable();
        }
        check(body);
        String openid = body.path("openid").asText("");
        String unionid = body.path("unionid").asText("");
        if (!openid.matches("[A-Za-z0-9_-]{1,128}")
                || (!unionid.isEmpty() && !unionid.matches("[A-Za-z0-9_-]{1,128}"))) throw unavailable();
        return new Identity(openid, unionid.isEmpty() ? null : unionid);
    }

    public Phone exchangePhone(String code) {
        requireEnabled();
        String token = accessToken();
        JsonNode body;
        try {
            body = client.post().uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                    .queryParam("access_token", token).build()).body(Map.of("code", code))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            // 请求可能已消费一次性 code，不能自动重放。
            throw unavailable();
        }
        if (body != null && (body.path("errcode").asInt() == 40001 || body.path("errcode").asInt() == 42001)) {
            redis.unlock(tokenKey(), token); // 只移除本次使用的旧缓存，不能删掉并发刷新的 Token。
        }
        check(body);
        JsonNode info = body.path("phone_info");
        JsonNode watermark = info.path("watermark");
        long timestamp = watermark.path("timestamp").asLong(0);
        long now = Instant.now().getEpochSecond();
        if (!properties.getAppId().equals(watermark.path("appid").asText())
                || timestamp < now - 300 || timestamp > now + 60) throw unavailable();
        String country = info.path("countryCode").asText("");
        String number = info.path("purePhoneNumber").asText("");
        if (!country.matches("[1-9][0-9]{0,2}") || !number.matches("[0-9]{4,14}")
                || country.length() + number.length() > 15) throw unavailable();
        return new Phone(country, number);
    }

    private String tokenKey() { return "auth:wechat:token:" + properties.getAppId(); }

    private String accessToken() {
        String cached = redis.get(tokenKey());
        if (StringUtils.hasText(cached)) return cached;
        String[] result = new String[1];
        boolean acquired = locks.tryRun(tokenKey() + ":lock", () -> {
            result[0] = redis.get(tokenKey());
            if (StringUtils.hasText(result[0])) return;
            JsonNode body;
            try {
                body = client.post().uri("/cgi-bin/stable_token").body(Map.of(
                        "grant_type", "client_credential", "appid", properties.getAppId(),
                        "secret", properties.getAppSecret(), "force_refresh", false))
                        .retrieve().body(JsonNode.class);
            } catch (RestClientException e) {
                throw unavailable();
            }
            check(body);
            String token = body.path("access_token").asText("");
            long seconds = body.path("expires_in").asLong(0);
            if (!StringUtils.hasText(token) || seconds <= 60) throw unavailable();
            redis.set(tokenKey(), token, seconds - 60, TimeUnit.SECONDS);
            result[0] = token;
        });
        if (!acquired) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信服务正在准备，请稍后重新尝试");
        return result[0];
    }

    private void check(JsonNode body) {
        if (body == null || !body.isObject()) throw unavailable();
        int code = body.path("errcode").asInt(0);
        if (code == 0) return;
        if (code == 40029 || code == 40163 || code == 40013) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信凭证无效或已使用，请重新微信登录");
        }
        throw unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信验证未完成，请重新微信登录或使用账号密码登录");
    }

    public record Identity(String openid, String unionid) {}
    public record Phone(String countryCode, String number) {}
}
