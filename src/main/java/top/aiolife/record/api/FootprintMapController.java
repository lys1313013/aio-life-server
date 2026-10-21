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
import top.aiolife.record.client.FootprintMapClient;
import top.aiolife.record.pojo.vo.FootprintLocationVO;

@RestController
@Validated
@RequestMapping("/life-footprints/map")
@RequiredArgsConstructor
@Tag(name = "生活足迹地图")
public class FootprintMapController {
    private final FootprintMapClient client;

    public record MapConfig(String browserKey, boolean searchEnabled, String coordinateSystem) {}

    @GetMapping("/config")
    public ApiResponse<MapConfig> config() {
        return ApiResponse.success(new MapConfig(client.browserKey(), client.available(), "GCJ02"));
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
