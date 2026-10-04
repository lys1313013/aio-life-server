package top.aiolife.system.api;

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

    @GetMapping
    public ApiResponse<List<HomeCardPreferenceVO>> get() {
        return ApiResponse.success(service.get(StpUtil.getLoginIdAsLong()));
    }
    @PutMapping("/order")
    public ApiResponse<List<HomeCardPreferenceVO>> reorder(@Valid @RequestBody HomeCardOrderReq request) {
        return ApiResponse.success(service.reorder(StpUtil.getLoginIdAsLong(), request.group(), request.keys()));
    }
    @PutMapping("/{key}")
    public ApiResponse<List<HomeCardPreferenceVO>> toggle(@PathVariable String key, @Valid @RequestBody HomeCardToggleReq request) {
        return ApiResponse.success(service.toggle(StpUtil.getLoginIdAsLong(), key, request.enabled()));
    }
    @DeleteMapping
    public ApiResponse<List<HomeCardPreferenceVO>> reset() {
        return ApiResponse.success(service.reset(StpUtil.getLoginIdAsLong()));
    }
}
