package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintTencentConfigService;

/** 腾讯 WebService 备用查询，仅使用搜索解析 Key。 */
@Component
public class TencentFootprintMapProvider extends AbstractFootprintMapProvider {
    private final FootprintTencentConfigService config;

    @Autowired
    public TencentFootprintMapProvider(FootprintTencentConfigService config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    TencentFootprintMapProvider(FootprintTencentConfigService config, ObjectMapper json, HttpClient http) {
        super(json, http);
        this.config = config;
    }

    @Override public boolean available() { return config.searchEnabled(); }
    @Override protected String origin() { return "https://apis.map.qq.com"; }
    @Override protected String keyParameter() { return "key"; }
    @Override protected String serviceKey() { return config.serviceKey(); }
    @Override protected int timeoutMs() { return config.timeoutMs(); }
    @Override protected boolean quotaExceeded(int status) { return status == 121; }

    @Override
    public List<FootprintLocationVO> search(String keyword, String region) {
        // 输入提示支持全国检索；选择城市时严格限制在该城市。
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("keyword", keyword);
        parameters.put("page_size", "10");
        parameters.put("page_index", "1");
        if (region != null && !region.isBlank() && !"全国".equals(region)) {
            parameters.put("region", region);
            parameters.put("region_fix", "1");
        }
        var root = query("/ws/place/v1/suggestion", parameters);
        if (!root.path("data").isArray()) throw unavailable("地图搜索返回格式无效，请稍后重试");
        var result = new ArrayList<FootprintLocationVO>();
        for (var item : root.path("data")) {
            if (!hasLocation(item.path("location"))) continue;
            result.add(location(item, item.path("title").asText(), item.path("address").asText(),
                    item.path("city").asText(), item.path("id").asText(), true, "POI"));
        }
        return result;
    }

    @Override
    public FootprintLocationVO geocode(String address) {
        if (address.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析地址过长，请保留城市、街道和门牌，楼栋房号可在选点后补充");
        }
        var root = query("/ws/geocoder/v1/", Map.of("address", address));
        var result = root.path("result");
        if (!hasLocation(result.path("location"))) throw unavailable("未找到有效位置，请补充地址或在地图选点");
        int level = result.path("level").asInt();
        return location(result, address, address, result.path("address_components").path("city").asText(), "",
                result.path("reliability").asInt() >= 7 && level >= 9, levelName(level));
    }

    @Override
    public FootprintLocationVO reverse(double longitude, double latitude) {
        var root = query("/ws/geocoder/v1/", Map.of("location", latitude + "," + longitude, "get_poi", "0"));
        var result = root.path("result");
        var address = result.path("address").asText();
        if (address.isBlank()) throw unavailable("暂未获取到地址，可手动填写");
        // 逆解析只补充描述，不能用上游返回的道路或 POI 中心点移动用户的选点。
        return new FootprintLocationVO(address, address, result.path("address_component").path("city").asText(),
                longitude, latitude, "GCJ02", null, true, "选点",
                new FootprintLocationVO.Region(result.path("ad_info").path("adcode").asText(),
                        null, result.path("address_reference").path("town").path("title").asText()));
    }

    private String levelName(int level) {
        return switch (level) {
            case 1 -> "城市";
            case 2 -> "区县";
            case 3 -> "乡镇街道";
            case 4 -> "村社区";
            case 5 -> "开发区";
            case 6 -> "商圈";
            case 7 -> "道路";
            case 8 -> "道路附属点";
            case 9 -> "门址";
            case 10 -> "小区大厦";
            case 11 -> "POI";
            default -> "未知";
        };
    }

}
