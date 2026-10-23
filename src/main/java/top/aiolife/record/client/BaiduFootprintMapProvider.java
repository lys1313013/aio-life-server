package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService;

/** 百度优先查询；请求显式指定 GCJ02，凭据只在后端使用。 */
@Component
public class BaiduFootprintMapProvider extends AbstractFootprintMapProvider {
    private final FootprintMapConfigService config;
    private final BaiduMapQuotaStore quotas;

    @Autowired
    public BaiduFootprintMapProvider(FootprintMapConfigService config, ObjectMapper json, BaiduMapQuotaStore quotas) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), quotas);
    }

    BaiduFootprintMapProvider(FootprintMapConfigService config, ObjectMapper json, HttpClient http, BaiduMapQuotaStore quotas) {
        super(json, http);
        this.config = config;
        this.quotas = quotas;
    }

    @Override public boolean available() { return config.searchEnabled(); }
    @Override protected String origin() { return "https://api.map.baidu.com"; }
    @Override protected String keyParameter() { return "ak"; }
    @Override protected String serviceKey() { return config.serviceKey(); }
    @Override protected int timeoutMs() { return config.timeoutMs(); }
    @Override protected boolean quotaExceeded(int status) { return status == 4 || status == 301 || status == 302; }

    @Override protected boolean concurrencyLimited(int status) { return status == 401 || status == 402; }
    @Override protected boolean fallbackOnHttpRateLimit() { return true; }

    @Override
    protected JsonNode query(String path, Map<String, String> values) {
        // 状态检查、真实请求、状态写入共用同一 AK 快照，避免切换 AK 时污染新凭据。
        String key = serviceKey();
        if (key == null || key.isBlank()) throw unavailable("地图搜索解析凭据尚未配置");
        var ticket = quotas.beforeQuery(key, path);
        try {
            return queryWithKey(path, values, key);
        } catch (MapQuotaExceededException exhausted) {
            quotas.recordExhausted(ticket, exhausted.upstreamStatus());
            throw exhausted;
        }
    }

    @Override
    public List<FootprintLocationVO> search(String keyword, String region) {
        // 全国使用明确支持“全国”的检索接口；指定城市时使用区域检索 3.0。
        var path = "全国".equals(region) ? "/place/v2/search" : "/place/v3/region";
        var root = query(path, Map.of("query", keyword, "region", region,
                "ret_coordtype", "gcj02ll", "page_size", "10", "scope", "1"));
        if (!root.path("results").isArray()) throw unavailable("地图搜索返回格式无效，请稍后重试");
        var result = new ArrayList<FootprintLocationVO>();
        for (var item : root.path("results")) {
            if (!hasLocation(item.path("location"))) continue;
            result.add(location(item, item.path("name").asText(), item.path("address").asText(),
                    item.path("city").asText(), item.path("uid").asText(), true, "POI"));
        }
        return result;
    }

    @Override
    public FootprintLocationVO geocode(String address) {
        if (address.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析地址过长，请保留城市、街道和门牌，楼栋房号可在选点后补充");
        }
        var root = query("/geocoding/v3/", Map.of("address", address, "ret_coordtype", "gcj02ll"));
        var result = root.path("result");
        if (!hasLocation(result.path("location"))) throw unavailable("未找到有效位置，请补充地址或在地图选点");
        return location(result, address, address, "", "", result.path("precise").asInt() == 1,
                result.path("level").asText());
    }

    @Override
    public FootprintLocationVO reverse(double longitude, double latitude) {
        var root = query("/reverse_geocoding/v3/", Map.of("location", latitude + "," + longitude,
                "coordtype", "gcj02ll", "ret_coordtype", "gcj02ll", "extensions_poi", "0", "region_data_source", "1"));
        var result = root.path("result");
        var address = result.path("formatted_address").asText();
        if (address.isBlank()) throw unavailable("暂未获取到地址，可手动填写");
        // 逆解析只补充描述，不能用上游返回的道路或 POI 中心点移动用户的选点。
        return new FootprintLocationVO(address, address, result.path("addressComponent").path("city").asText(),
                longitude, latitude, "GCJ02", null, true, "选点",
                new FootprintLocationVO.Region(result.path("addressComponent").path("adcode").asText(),
                        result.path("addressComponent").path("town_code").asText(),
                        result.path("addressComponent").path("town").asText()));
    }

}
