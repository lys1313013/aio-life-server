package top.aiolife.sso.api;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.entity.UserEntity;
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
import top.aiolife.sso.service.IUserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/4/3
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 登录
     *
     * @author Lys
     * @date 2025/4/4
     */
    @PostMapping("/auth/login")
    public ApiResponse<UserLoginVO> login(@RequestBody LoginReq loginReq, HttpServletRequest request) {
        String ip = getIp(request);
        return ApiResponse.success(userService.login(loginReq, ip));
    }

    /**
     * 修改密码
     */
    @PostMapping("/auth/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordReq changePasswordReq) {
        long id = StpUtil.getLoginIdAsLong();
        userService.changePassword(id, changePasswordReq);
        return ApiResponse.success();
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/auth/info")
    public ApiResponse<UserInfoVO> authInfo() {
        return info();
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/user/info")
    public ApiResponse<UserInfoVO> info() {
        long id = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(userService.getUserInfo(id));
    }

    /**
     * 获取用户基本信息
     */
    @GetMapping("/user/{id}/basic")
    public ApiResponse<UserBasicInfoVO> basicInfo(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserBasicInfo(id));
    }

    @GetMapping("/auth/codes")
    public ApiResponse<Void> codes() {
        Map<String, Object> data = new HashMap<>();
        data.put("data", new String[]{"AC_100100", "AC_100110", "AC_100120", "AC_100010"});
        Map<String, Object> map = new HashMap<>();
        map.put("rscode", "0");
        return ApiResponse.success();
    }


    @PostMapping("/auth/logout")
    public ApiResponse<Map<String, Object>> logout() {
        StpUtil.logout();
        return ApiResponse.success();
    }

    /**
     * 从请求头中获取IP
     */
    public String getIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * 普通更新用户信息
     */
    @PutMapping("/users")
    public ApiResponse<Void> modify(@RequestBody UpdateUserReq req) {
        long id = StpUtil.getLoginIdAsLong();
        UserEntity userEntity = new UserEntity();
        userEntity.setId(id);
        userEntity.setNickname(req.getNickname());
        userEntity.setIntroduction(req.getIntroduction());
        userEntity.setAvatar(req.getAvatar());
        userService.updateUser(userEntity);
        return ApiResponse.success();
    }

    /**
     * 查询是否已设置二级密码
     */
    @GetMapping("/auth/secondary-password/status")
    public ApiResponse<Map<String, Boolean>> secondaryPasswordStatus() {
        long id = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(Map.of("hasPassword", userService.hasSecondaryPassword(id)));
    }

    /**
     * 设置/修改二级密码
     */
    @PutMapping("/auth/secondary-password")
    public ApiResponse<Void> setSecondaryPassword(@RequestBody SetSecondaryPasswordReq req) {
        long id = StpUtil.getLoginIdAsLong();
        userService.setSecondaryPassword(id, req.getPassword(), req.getOldPassword());
        return ApiResponse.success();
    }

    /**
     * 验证二级密码，解锁菜单
     */
    @PostMapping("/auth/secondary-verify")
    public ApiResponse<Map<String, Object>> secondaryVerify(@RequestBody SecondaryVerifyReq req) {
        long id = StpUtil.getLoginIdAsLong();
        userService.verifySecondaryPassword(id, req.getPassword(), req.getMenuPath());
        return ApiResponse.success(Map.of("menuPath", req.getMenuPath()));
    }

    /**
     * 获取当前用户锁定的菜单 ID 列表
     */
    @GetMapping("/auth/secondary-lock/menus")
    public ApiResponse<List<Long>> getSecondaryLockMenus() {
        long id = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(userService.getSecondaryLockMenuIds(id));
    }

    /**
     * 保存当前用户锁定的菜单 ID 列表
     */
    @PutMapping("/auth/secondary-lock/menus")
    public ApiResponse<Void> saveSecondaryLockMenus(@RequestBody SaveSecondaryLockMenusReq req) {
        long id = StpUtil.getLoginIdAsLong();
        userService.saveSecondaryLockMenus(id, req.getMenuIds());
        return ApiResponse.success();
    }

    /**
     * 发送重置二级密码验证码（发到当前用户绑定的邮箱）
     */
    @PostMapping("/auth/send-reset-secondary-password-code")
    public ApiResponse<Void> sendResetSecondaryPasswordCode(HttpServletRequest request) {
        long id = StpUtil.getLoginIdAsLong();
        String ip = getIp(request);
        userService.sendResetSecondaryPasswordCode(id, ip);
        return ApiResponse.success();
    }

    /**
     * 通过邮箱验证码重置二级密码
     */
    @PostMapping("/auth/reset-secondary-password")
    public ApiResponse<Void> resetSecondaryPassword(@RequestBody ResetSecondaryPasswordReq req) {
        long id = StpUtil.getLoginIdAsLong();
        userService.resetSecondaryPassword(id, req.getCode(), req.getPassword());
        return ApiResponse.success();
    }

}
