package top.aiolife.sso.wechat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.util.RedisUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 微信服务端凭证交换，不向调用方透传微信响应、session_key 或包含密钥的异常。 */
@Component
@Slf4j
public class WechatMiniClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WechatMiniProperties properties;
    private final RedisUtil redis;
    private final DistributedLockExecutor locks;
    private final RestClient client;

    @Autowired
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
            body = readJson(client.get().uri(uri -> uri.path("/sns/jscode2session")
                    .queryParam("appid", properties.getAppId()).queryParam("secret", properties.getAppSecret())
                    .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
                    .retrieve(), "code2Session");
        } catch (RestClientException e) {
            log.warn("微信接口调用失败 operation=code2Session type={}", e.getClass().getSimpleName());
            throw unavailable();
        }
        check(body, "code2Session");
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
            body = readJson(client.post().uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                    .queryParam("access_token", token).build()).body(Map.of("code", code))
                    .retrieve(), "getuserphonenumber");
        } catch (RestClientException e) {
            log.warn("微信接口调用失败 operation=getuserphonenumber type={}", e.getClass().getSimpleName());
            // 请求可能已消费一次性 code，不能自动重放。
            throw unavailable();
        }
        if (body != null && (body.path("errcode").asInt() == 40001 || body.path("errcode").asInt() == 42001)) {
            redis.unlock(tokenKey(), token); // 只移除本次使用的旧缓存，不能删掉并发刷新的 Token。
        }
        check(body, "getuserphonenumber");
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

    /** 微信返回图片或 JSON 错误；只接收 PNG/JPEG，绝不将上游错误正文返回前端。 */
    public String webLoginQrCode(String scene) {
        requireEnabled();
        if (!scene.matches("[a-f0-9]{32}")
                || !Set.of("release", "trial", "develop").contains(properties.getWebScanEnvVersion())) {
            throw unavailable();
        }
        String token = accessToken();
        byte[] bytes;
        try {
            bytes = client.post().uri(uri -> uri.path("/wxa/getwxacodeunlimit")
                    .queryParam("access_token", token).build())
                    .body(Map.of("scene", scene, "page", "pages/auth/web-login", "width", 280,
                            "check_path", "release".equals(properties.getWebScanEnvVersion()), "env_version", properties.getWebScanEnvVersion()))
                    .retrieve().body(byte[].class);
        } catch (RestClientException e) {
            log.warn("微信接口调用失败 operation=getwxacodeunlimit type={}", e.getClass().getSimpleName());
            throw unavailable();
        }
        if (bytes == null || bytes.length < 8 || bytes.length > 1024 * 1024) throw unavailable();
        String mime = null;
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) mime = "image/png";
        else if (bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) mime = "image/jpeg";
        if (mime == null) {
            try {
                JsonNode error = JSON.readTree(bytes);
                if (error != null && (error.path("errcode").asInt() == 40001 || error.path("errcode").asInt() == 42001)) {
                    redis.unlock(tokenKey(), token);
                }
                check(error, "getwxacodeunlimit");
            } catch (IOException e) {
                log.warn("微信接口响应无法解析 operation=getwxacodeunlimit");
            }
            throw unavailable();
        }
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
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
                body = readJson(client.post().uri("/cgi-bin/stable_token").body(Map.of(
                        "grant_type", "client_credential", "appid", properties.getAppId(),
                        "secret", properties.getAppSecret(), "force_refresh", false))
                        .retrieve(), "stable_token");
            } catch (RestClientException e) {
                log.warn("微信接口调用失败 operation=stable_token type={}", e.getClass().getSimpleName());
                throw unavailable();
            }
            check(body, "stable_token");
            String token = body.path("access_token").asText("");
            long seconds = body.path("expires_in").asLong(0);
            if (!StringUtils.hasText(token) || seconds <= 60) throw unavailable();
            redis.set(tokenKey(), token, seconds - 60, TimeUnit.SECONDS);
            result[0] = token;
        });
        if (!acquired) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信服务正在准备，请稍后重新尝试");
        return result[0];
    }

    private JsonNode readJson(RestClient.ResponseSpec response, String operation) {
        // 微信实际可能用 text/plain 返回 JSON，不能依赖响应 Content-Type 选择 Jackson 转换器。
        String body = response.body(String.class);
        if (!StringUtils.hasText(body)) throw unavailable();
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            // 异常消息、响应正文和请求 URL 可能含凭证，只记录固定阶段与错误类别。
            log.warn("微信接口响应无法解析 operation={} type=invalid_json", operation);
            throw unavailable();
        }
    }

    private void check(JsonNode body, String operation) {
        if (body == null || !body.isObject()) throw unavailable();
        int code = body.path("errcode").asInt(0);
        if (code == 0) return;
        log.warn("微信接口返回失败 operation={} errcode={}", operation, code);
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
