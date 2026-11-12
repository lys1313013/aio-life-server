package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;

import top.aiolife.system.service.HomeCardPreferenceService;
import top.aiolife.system.pojo.vo.HomeCardPreferenceVO;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import top.aiolife.system.pojo.req.HomeCardToggleReq;
import top.aiolife.system.pojo.req.HomeCardOrderReq;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;

import java.util.List;

/** User identity is always obtained from the authenticated session. */
@RestController
@RequestMapping("/home/cards")
@RequiredArgsConstructor
public class HomeCardPreferenceController {
    private final HomeCardPreferenceService service;

    @Operation(summary = "查询首页卡片设置")
    @GetMapping
    public ApiResponse<List<HomeCardPreferenceVO>> get() {
        return ApiResponse.success(service.get(StpUtil.getLoginIdAsLong()));
    }
    @Operation(summary = "调整首页卡片排序")
    @PutMapping("/order")
    public ApiResponse<List<HomeCardPreferenceVO>> reorder(@Valid @RequestBody HomeCardOrderReq request) {
        return ApiResponse.success(service.reorder(StpUtil.getLoginIdAsLong(), request.group(), request.keys()));
    }
    @Operation(summary = "设置首页卡片启用状态")
    @PutMapping("/{key}")
    public ApiResponse<List<HomeCardPreferenceVO>> toggle(@PathVariable String key, @Valid @RequestBody HomeCardToggleReq request) {
        return ApiResponse.success(service.toggle(StpUtil.getLoginIdAsLong(), key, request.isEnabled()));
    }
    @Operation(summary = "重置首页卡片设置")
    @DeleteMapping
    public ApiResponse<List<HomeCardPreferenceVO>> reset() {
        return ApiResponse.success(service.reset(StpUtil.getLoginIdAsLong()));
    }
}
