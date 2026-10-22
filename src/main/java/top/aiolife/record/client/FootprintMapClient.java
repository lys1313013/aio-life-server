package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService;

/** 固定上游、有限超时及额度保护；不记录地址、坐标、AK 或上游异常中的完整 URL。 */
@Component
public class FootprintMapClient {
    private final FootprintMapConfigService config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Semaphore concurrent = new Semaphore(8);
    private final Cache<Long, AtomicInteger> limits = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    @Autowired
    public FootprintMapClient(FootprintMapConfigService config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    FootprintMapClient(FootprintMapConfigService config, ObjectMapper json, HttpClient http) {
        this.config = config;
        this.json = json;
        this.http = http;
    }

    public boolean available() { return config.searchEnabled(); }
    public FootprintMapConfigService.TileSource tileSource() { return config.tileSource(); }

    public List<FootprintLocationVO> search(long userId, String keyword, String region) {
        // 全国使用明确支持“全国”的检索接口；指定城市时使用区域检索 3.0。
        var path = "全国".equals(region) ? "/place/v2/search" : "/place/v3/region";
        var root = query(userId, path, Map.of("query", keyword, "region", region,
                "ret_coordtype", "gcj02ll", "page_size", "10", "scope", "1"));
        var result = new ArrayList<FootprintLocationVO>();
        for (var item : root.path("results")) {
            if (!hasLocation(item.path("location"))) continue;
            result.add(location(item, item.path("name").asText(), item.path("address").asText(),
                    item.path("city").asText(), item.path("uid").asText(), true, "POI"));
        }
        return result;
    }

    public FootprintLocationVO geocode(long userId, String address) {
        if (address.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析地址过长，请保留城市、街道和门牌，楼栋房号可在选点后补充");
        }
        var root = query(userId, "/geocoding/v3/", Map.of("address", address, "ret_coordtype", "gcj02ll"));
        var result = root.path("result");
        if (!hasLocation(result.path("location"))) throw unavailable("未找到有效位置，请补充地址或在地图选点");
        return location(result, address, address, "", "", result.path("precise").asInt() == 1,
                result.path("level").asText());
    }

    public FootprintLocationVO reverse(long userId, double longitude, double latitude) {
        var root = query(userId, "/reverse_geocoding/v3/", Map.of("location", latitude + "," + longitude,
                "coordtype", "gcj02ll", "ret_coordtype", "gcj02ll", "extensions_poi", "0"));
        var result = root.path("result");
        var address = result.path("formatted_address").asText();
        if (address.isBlank()) throw unavailable("暂未获取到地址，可手动填写");
        // 逆解析只补充描述，不能用上游返回的道路或 POI 中心点移动用户的选点。
        return new FootprintLocationVO(address, address, result.path("addressComponent").path("city").asText(),
                longitude, latitude, "GCJ02", null, true, "选点");
    }

    private FootprintLocationVO location(JsonNode item, String name, String address, String city,
                                          String uid, boolean precise, String level) {
        var location = item.path("location");
        return new FootprintLocationVO(name, address, city, location.path("lng").doubleValue(),
                location.path("lat").doubleValue(), "GCJ02", uid.isBlank() ? null : uid, precise, level);
    }

    private boolean hasLocation(JsonNode node) {
        if (!node.path("lng").isNumber() || !node.path("lat").isNumber()) return false;
        double lng = node.path("lng").doubleValue(), lat = node.path("lat").doubleValue();
        return Double.isFinite(lng) && Double.isFinite(lat) && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
    }

    private JsonNode query(long userId, String path, Map<String, String> values) {
        String serviceAk = config.serviceKey();
        if (serviceAk.isBlank()) throw unavailable("地址搜索尚未配置，请管理员在系统配置中填写服务端 AK");
        if (limits.get(userId, ignored -> new AtomicInteger()).incrementAndGet() > 60 || !concurrent.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "搜索较频繁，请稍后重试");
        }
        try {
            var parameters = new LinkedHashMap<>(values);
            parameters.put("ak", serviceAk);
            parameters.put("output", "json");
            var query = parameters.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .reduce((left, right) -> left + "&" + right).orElse("");
            var request = HttpRequest.newBuilder(URI.create("https://api.map.baidu.com" + path + "?" + query))
                    .timeout(Duration.ofMillis(config.timeoutMs())).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw unavailable("地图服务暂不可用，请重试或手动选点");
            var root = json.readTree(response.body());
            if (root == null || !root.path("status").isIntegralNumber() || root.path("status").asInt() != 0) {
                throw unavailable("地图服务未返回有效结果，请重试或手动选点");
            }
            return root;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("地图请求已中断，请重试");
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable("地图服务暂不可用，请重试或手动选点");
        } finally {
            concurrent.release();
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
