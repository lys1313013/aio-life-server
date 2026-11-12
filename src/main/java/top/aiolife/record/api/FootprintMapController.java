package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

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

    @Operation(summary = "查询足迹地图配置")
    @GetMapping("/config")
    public ApiResponse<MapConfig> config(HttpServletRequest request, HttpServletResponse response) {
        var browserConfig = tencentConfig.browserConfig();
        if (browserConfig.enabled()) proxySession.issue(request, response);
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(new MapConfig(client.available(), "GCJ02", client.tileSource(), browserConfig));
    }

    @Operation(summary = "搜索足迹地图地点")
    @GetMapping("/search")
    public ApiResponse<List<FootprintLocationVO>> search(@Parameter(description = "地点关键词") @RequestParam @NotBlank @Size(max = 100) String keyword,
                                                       @Parameter(description = "搜索地区") @RequestParam(defaultValue = "全国") @Size(max = 100) String region) {
        return ApiResponse.success(client.search(StpUtil.getLoginIdAsLong(), keyword, region));
    }

    @Operation(summary = "将地址转换为地图坐标")
    @GetMapping("/geocode")
    public ApiResponse<FootprintLocationVO> geocode(@Parameter(description = "待定位地址") @RequestParam @NotBlank @Size(max = 128) String address) {
        return ApiResponse.success(client.geocode(StpUtil.getLoginIdAsLong(), address));
    }

    @Operation(summary = "将地图坐标转换为地址")
    @GetMapping("/reverse")
    public ApiResponse<FootprintLocationVO> reverse(@Parameter(description = "GCJ02 经度") @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
                                                  @Parameter(description = "GCJ02 纬度") @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude) {
        return ApiResponse.success(client.reverse(StpUtil.getLoginIdAsLong(), longitude, latitude));
    }
}
