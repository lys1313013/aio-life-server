package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import top.aiolife.record.client.FootprintMapClient;
import top.aiolife.record.filter.TencentMapProxySessionFilter;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService.TileSource;
import top.aiolife.system.service.FootprintTencentConfigService;
import top.aiolife.system.service.FootprintTencentConfigService.BrowserConfig;

@RestController
@Validated
@RequestMapping("/life-footprints/map")
@RequiredArgsConstructor
@Tag(name = "生活足迹地图")
public class FootprintMapController {
    private final FootprintMapClient client;
    private final FootprintTencentConfigService tencentConfig;
    private final TencentMapProxySessionFilter proxySession;

    public record MapConfig(boolean searchEnabled, String coordinateSystem, TileSource tiles, BrowserConfig tencent) {}

    @GetMapping("/config")
    public ApiResponse<MapConfig> config(HttpServletRequest request, HttpServletResponse response) {
        var browserConfig = tencentConfig.browserConfig();
        if (browserConfig.enabled()) proxySession.issue(request, response);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(new MapConfig(client.available(), "GCJ02", client.tileSource(), browserConfig));
    }

    @GetMapping("/search")
    public ApiResponse<List<FootprintLocationVO>> search(@RequestParam @NotBlank @Size(max = 100) String keyword,
                                                       @RequestParam(defaultValue = "全国") @Size(max = 100) String region) {
        return ApiResponse.success(client.search(StpUtil.getLoginIdAsLong(), keyword, region));
    }

    @GetMapping("/geocode")
    public ApiResponse<FootprintLocationVO> geocode(@RequestParam @NotBlank @Size(max = 128) String address) {
        return ApiResponse.success(client.geocode(StpUtil.getLoginIdAsLong(), address));
    }

    @GetMapping("/reverse")
    public ApiResponse<FootprintLocationVO> reverse(@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
                                                  @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude) {
        return ApiResponse.success(client.reverse(StpUtil.getLoginIdAsLong(), longitude, latitude));
    }
}
