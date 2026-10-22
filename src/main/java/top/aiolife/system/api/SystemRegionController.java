package top.aiolife.system.api;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.system.pojo.vo.SystemRegionVO;
import top.aiolife.system.service.SystemRegionService;

/** 登录后读取公共地区目录；不调用地图供应商。 */
@Validated
@RestController
@RequestMapping("/system/regions")
@RequiredArgsConstructor
public class SystemRegionController {
    private final SystemRegionService service;
    @GetMapping
    public ApiResponse<List<SystemRegionVO>> children(
            @RequestParam(defaultValue = "0") @Pattern(regexp = "[0-9]{0,12}") String parentCode) {
        return ApiResponse.success(service.children(parentCode));
    }
    @GetMapping("/{code}/path")
    public ApiResponse<List<SystemRegionVO>> path(
            @PathVariable @Pattern(regexp = "[0-9]{2,12}") String code) {
        return ApiResponse.success(service.path(code));
    }
}
