package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.ResetSecondaryPasswordReq;
import top.aiolife.sso.pojo.req.SaveSecondaryLockMenusReq;
import top.aiolife.sso.pojo.req.SecondaryVerifyReq;
import top.aiolife.sso.pojo.req.SetSecondaryPasswordReq;
import top.aiolife.sso.pojo.req.UpdateUserReq;
import top.aiolife.sso.pojo.vo.UserBasicInfoVO;
import top.aiolife.sso.pojo.vo.UserInfoVO;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.service.AuthService;
import top.aiolife.sso.service.IUserService;

import java.util.List;
import java.util.Map;

/**
 * 用户认证与资料控制器
 *
 * @author Lys
 * @date 2025/4/3
 */
@Tag(name = "用户")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @Operation(summary = "账号密码登录")
    @PostMapping("/auth/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody LoginReq loginReq, HttpServletRequest request) {
        return ApiResponse.success(userService.login(loginReq, AuthService.clientIp(request)));
    }

    @Operation(summary = "修改登录密码")
    @PostMapping("/auth/change-password")
    public ApiResponse<Void> changePassword(@CurrentUserId String userId,
                                            @Valid @RequestBody ChangePasswordReq changePasswordReq) {
        userService.changePassword(IdCodec.decode(userId), changePasswordReq);
        return ApiResponse.success();
    }

    @Operation(summary = "查询当前登录用户信息")
    @GetMapping("/auth/info")
    public ApiResponse<UserInfoVO> authInfo(@CurrentUserId String userId) {
        return ApiResponse.success(userService.getUserInfo(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询当前用户资料")
    @GetMapping("/user/info")
    public ApiResponse<UserInfoVO> info(@CurrentUserId String userId) {
        return ApiResponse.success(userService.getUserInfo(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询指定用户基本信息")
    @GetMapping("/user/{id}/basic")
    public ApiResponse<UserBasicInfoVO> basicInfo(@PathVariable String id) {
        return ApiResponse.success(userService.getUserBasicInfo(IdCodec.decode(id)));
    }

    @Operation(summary = "查询当前用户权限码")
    @GetMapping("/auth/codes")
    public ApiResponse<Void> codes() {
        return ApiResponse.success();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/auth/logout")
    public ApiResponse<Map<String, Object>> logout() {
        AuthService.logout();
        return ApiResponse.success();
    }

    @Operation(summary = "更新当前用户资料")
    @PutMapping("/users")
    public ApiResponse<Void> modify(@CurrentUserId String userId, @Valid @RequestBody UpdateUserReq req) {
        userService.updateProfile(IdCodec.decode(userId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "查询二级密码设置状态")
    @GetMapping("/auth/secondary-password/status")
    public ApiResponse<Map<String, Boolean>> secondaryPasswordStatus(@CurrentUserId String userId) {
        return ApiResponse.success(Map.of("hasPassword", userService.hasSecondaryPassword(IdCodec.decode(userId))));
    }

    @Operation(summary = "设置或修改二级密码")
    @PutMapping("/auth/secondary-password")
    public ApiResponse<Void> setSecondaryPassword(@CurrentUserId String userId,
                                                  @Valid @RequestBody SetSecondaryPasswordReq req) {
        userService.setSecondaryPassword(IdCodec.decode(userId), req.getPassword(), req.getOldPassword());
        return ApiResponse.success();
    }

    @Operation(summary = "验证二级密码并解锁菜单")
    @PostMapping("/auth/secondary-verify")
    public ApiResponse<Map<String, Object>> secondaryVerify(@CurrentUserId String userId,
                                                            @Valid @RequestBody SecondaryVerifyReq req) {
        userService.verifySecondaryPassword(IdCodec.decode(userId), req.getPassword(), req.getMenuPath());
        return ApiResponse.success(Map.of("menuPath", req.getMenuPath()));
    }

    @Operation(summary = "查询已锁定菜单")
    @GetMapping("/auth/secondary-lock/menus")
    public ApiResponse<List<Long>> getSecondaryLockMenus(@CurrentUserId String userId) {
        return ApiResponse.success(userService.getSecondaryLockMenuIds(IdCodec.decode(userId)));
    }

    @Operation(summary = "保存菜单锁定配置")
    @PutMapping("/auth/secondary-lock/menus")
    public ApiResponse<Void> saveSecondaryLockMenus(@CurrentUserId String userId,
                                                    @Valid @RequestBody SaveSecondaryLockMenusReq req) {
        userService.saveSecondaryLockMenus(IdCodec.decode(userId), req.getMenuIds(), req.getSecondaryPassword());
        return ApiResponse.success();
    }

    @Operation(summary = "发送二级密码重置邮箱验证码")
    @PostMapping("/auth/send-reset-secondary-password-code")
    public ApiResponse<Void> sendResetSecondaryPasswordCode(@CurrentUserId String userId, HttpServletRequest request) {
        userService.sendResetSecondaryPasswordCode(IdCodec.decode(userId), AuthService.clientIp(request));
        return ApiResponse.success();
    }

    @Operation(summary = "通过邮箱验证码重置二级密码")
    @PostMapping("/auth/reset-secondary-password")
    public ApiResponse<Void> resetSecondaryPassword(@CurrentUserId String userId,
                                                    @Valid @RequestBody ResetSecondaryPasswordReq req) {
        userService.resetSecondaryPassword(IdCodec.decode(userId), req.getCode(), req.getPassword());
        return ApiResponse.success();
    }
}
