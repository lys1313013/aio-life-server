package top.aiolife.record.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.service.FootprintTencentConfigService;

/** 腾讯 GL 官方代理：仅开放当前地图所需固定路由，不提供任意 URL 或 WebService 转发。
 * Key 不出现在响应、重定向、日志或异常中；不转发浏览器 Cookie/Authorization。
 */
@Component
public class TencentMapProxyClient {
    private static final Map<String, String> UPSTREAMS = Map.of(
            "", "https://pr.map.qq.com/pingd",
            "/checkKey", "https://apikey.map.qq.com/mkey/index.php/mkey/check",
            "/oversea", "https://overseactrl.map.qq.com/");
    private static final Set<String> CREDENTIALS = Set.of("key", "key1", "key2", "apikey", "subkey", "servicesk", "sig");
    private static final Pattern CALLBACK = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private final FootprintTencentConfigService config;
    private final HttpClient http;
    private final Semaphore concurrent = new Semaphore(16);
    private final Cache<Long, AtomicInteger> limits = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    private final Cache<String, AtomicInteger> globalLimits = Caffeine.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofMinutes(1)).build();

    @Autowired
    public TencentMapProxyClient(FootprintTencentConfigService config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    TencentMapProxyClient(FootprintTencentConfigService config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public ResponseEntity<byte[]> forward(long userId, String path, MultiValueMap<String, String> parameters) {
        String upstream = UPSTREAMS.get(path);
        if (upstream == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地图代理路径不存在");
        if (parameters.size() > 50 || parameters.entrySet().stream().anyMatch(e -> e.getKey().length() > 100
                || e.getValue().size() > 10 || e.getValue().stream().anyMatch(v -> v == null || v.length() > 4096))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地图代理参数无效");
        }
        for (String callback : new String[]{"cb", "callback"}) {
            if (parameters.getOrDefault(callback, List.of()).stream().anyMatch(v -> !CALLBACK.matcher(v).matches()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地图回调参数无效");
        }
        if (limits.get(userId, ignored -> new AtomicInteger()).incrementAndGet() > 240
                || globalLimits.get("requests", ignored -> new AtomicInteger()).incrementAndGet() > 2400) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "地图请求较频繁，请稍后重试");
        }
        String key = config.displayKey();
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "腾讯地图尚未配置");
        if (!concurrent.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "地图服务繁忙");
        try {
            var query = new StringBuilder();
            for (var entry : parameters.entrySet()) {
                if (CREDENTIALS.contains(entry.getKey().toLowerCase(Locale.ROOT))) continue;
                if (path.isEmpty() && entry.getKey().equalsIgnoreCase("appid")) continue;
                for (String value : entry.getValue()) append(query, entry.getKey(), value);
            }
            if (path.isEmpty()) append(query, "appid", "jsapi_v3");
            append(query, path.equals("/oversea") ? "apikey" : "key", key);
            var request = HttpRequest.newBuilder(URI.create(upstream + "?" + query))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (result.statusCode() < 200 || result.statusCode() >= 300 || result.body().length > 1024 * 1024) {
                throw unavailable();
            }
            // 海外接口可能返回 application/json;text/html;charset=UTF-8，忽略无效的中间片段。
            String contentType = result.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .orElse(MediaType.APPLICATION_JSON_VALUE);
            var type = MediaType.parseMediaType(contentType.split(";", 2)[0]);
            if (!Set.of("application/json", "application/javascript", "text/javascript", "text/plain", "image/gif")
                    .contains(type.getType() + "/" + type.getSubtype())) throw unavailable();
            byte[] body = result.body();
            if (!type.getType().equals("image")) {
                Charset charset = StandardCharsets.UTF_8;
                for (String part : contentType.split(";")) {
                    if (part.trim().toLowerCase(Locale.ROOT).startsWith("charset="))
                        charset = Charset.forName(part.trim().substring(8).replace("\"", ""));
                }
                body = new String(body, charset).replace(key, "").getBytes(charset);
                type = new MediaType(type, charset);
            }
            // 保留腾讯 JSON/JSONP 的业务状态供 SDK 校验，不将 HTTP 200 当作鉴权成功。
            return ResponseEntity.ok().contentType(type).header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff").body(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        } finally {
            concurrent.release();
        }
    }

    private static void append(StringBuilder query, String name, String value) {
        if (!query.isEmpty()) query.append('&');
        query.append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "腾讯地图服务暂不可用");
    }
}
