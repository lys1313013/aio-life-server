package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.system.pojo.req.UserMenuHiddenSaveReq;
import top.aiolife.system.pojo.vo.UserMenuPreferenceVO;
import top.aiolife.system.service.UserMenuPreferenceService;
import top.aiolife.system.service.MenuClient;

/**
 * 当前用户个人菜单显示设置；用户身份只能来自登录态。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "菜单偏好")
@RestController
@RequestMapping("/menu/preferences")
@RequiredArgsConstructor
public class UserMenuPreferenceController {
    private final UserMenuPreferenceService service;

    @Operation(summary = "查询菜单显示偏好")
    @GetMapping
    public ApiResponse<UserMenuPreferenceVO> get(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(service.get(IdCodec.decode(userId), MenuClient.parse(client)));
    }

    @Operation(summary = "保存菜单显示偏好")
    @PutMapping
    public ApiResponse<UserMenuPreferenceVO> save(
            @CurrentUserId String userId,
            @Valid @RequestBody UserMenuHiddenSaveReq req,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(service.save(
                IdCodec.decode(userId), req.getMenuIds(), MenuClient.parse(client)));
    }

    @Operation(summary = "重置菜单显示偏好")
    @DeleteMapping
    public ApiResponse<UserMenuPreferenceVO> reset(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(service.reset(IdCodec.decode(userId), MenuClient.parse(client)));
    }
}
