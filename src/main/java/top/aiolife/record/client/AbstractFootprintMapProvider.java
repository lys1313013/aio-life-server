package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;

/** 固定供应商上游；公共传输与坐标映射，不负责供应商选择。 */
abstract class AbstractFootprintMapProvider implements FootprintMapProvider {
    private final ObjectMapper json;
    private final HttpClient http;

    protected AbstractFootprintMapProvider(ObjectMapper json, HttpClient http) {
        this.json = json;
        this.http = http;
    }

    protected abstract String origin();
    protected abstract String keyParameter();
    protected abstract String serviceKey();
    protected abstract int timeoutMs();
    protected abstract boolean quotaExceeded(int status);

    protected boolean concurrencyLimited(int status) { return false; }
    protected boolean fallbackOnHttpRateLimit() { return false; }

    protected JsonNode query(String path, Map<String, String> values) {
        return queryWithKey(path, values, serviceKey());
    }

    protected final JsonNode queryWithKey(String path, Map<String, String> values, String key) {
        if (key == null || key.isBlank()) throw unavailable("地图搜索解析凭据尚未配置");
        try {
            var parameters = new LinkedHashMap<>(values);
            parameters.put(keyParameter(), key);
            parameters.put("output", "json");
            var query = parameters.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .reduce((left, right) -> left + "&" + right).orElse("");
            var request = HttpRequest.newBuilder(URI.create(origin() + path + "?" + query))
                    .timeout(Duration.ofMillis(timeoutMs())).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429 && fallbackOnHttpRateLimit())
                throw new MapFallbackException("地图查询并发受限，请稍后重试或手工选点");
            if (response.statusCode() != 200) throw unavailable("地图服务暂不可用，请重试或手动选点");
            var root = json.readTree(response.body());
            if (root == null || !root.path("status").isIntegralNumber() || !root.path("status").canConvertToInt())
                throw unavailable("地图服务未返回有效结果，请重试或手动选点");
            int status = root.path("status").intValue();
            if (quotaExceeded(status)) throw new MapQuotaExceededException(status);
            if (concurrencyLimited(status))
                throw new MapFallbackException("地图查询并发受限，请稍后重试或手工选点");
            if (status != 0) throw unavailable("地图服务未返回有效结果，请检查服务权限或稍后重试");
            return root;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("地图请求已中断，请重试");
        } catch (IOException | IllegalArgumentException exception) {
            // 不传递上游 URL、响应消息或原始异常，防止 Key、地址出现在日志和 API 中。
            throw unavailable("地图服务暂不可用，请重试或手动选点");
        }
    }

    protected final FootprintLocationVO location(JsonNode item, String name, String address, String city,
                                          String uid, boolean precise, String level) {
        var location = item.path("location");
        return new FootprintLocationVO(name, address, city, location.path("lng").doubleValue(),
                location.path("lat").doubleValue(), "GCJ02", uid.isBlank() ? null : uid, precise, level);
    }

    protected final boolean hasLocation(JsonNode node) {
        if (!node.path("lng").isNumber() || !node.path("lat").isNumber()) return false;
        double lng = node.path("lng").doubleValue(), lat = node.path("lat").doubleValue();
        return Double.isFinite(lng) && Double.isFinite(lat) && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
    }


    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    protected final ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
