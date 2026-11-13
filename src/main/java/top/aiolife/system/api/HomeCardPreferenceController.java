package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.system.service.HomeCardPreferenceService;
import top.aiolife.system.pojo.vo.HomeCardPreferenceVO;
import jakarta.validation.Valid;
import top.aiolife.system.pojo.req.HomeCardToggleReq;
import top.aiolife.system.pojo.req.HomeCardOrderReq;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 首页卡片设置控制器，用户身份始终来自登录态。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "首页卡片")
@RestController
@RequestMapping("/home/cards")
@RequiredArgsConstructor
public class HomeCardPreferenceController {
    private final HomeCardPreferenceService service;

    @Operation(summary = "查询首页卡片设置")
    @GetMapping
    public ApiResponse<List<HomeCardPreferenceVO>> get(@CurrentUserId String userId) {
        return ApiResponse.success(service.get(IdCodec.decode(userId)));
    }

    @Operation(summary = "调整首页卡片排序")
    @PutMapping("/order")
    public ApiResponse<List<HomeCardPreferenceVO>> reorder(@CurrentUserId String userId,
                                                           @Valid @RequestBody HomeCardOrderReq request) {
        return ApiResponse.success(service.reorder(IdCodec.decode(userId), request.group(), request.keys()));
    }

    @Operation(summary = "设置首页卡片启用状态")
    @PutMapping("/{key}")
    public ApiResponse<List<HomeCardPreferenceVO>> toggle(@CurrentUserId String userId, @PathVariable String key,
                                                          @Valid @RequestBody HomeCardToggleReq request) {
        return ApiResponse.success(service.toggle(IdCodec.decode(userId), key, request.isEnabled()));
    }

    @Operation(summary = "重置首页卡片设置")
    @DeleteMapping
    public ApiResponse<List<HomeCardPreferenceVO>> reset(@CurrentUserId String userId) {
        return ApiResponse.success(service.reset(IdCodec.decode(userId)));
    }
}
