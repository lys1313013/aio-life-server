package top.aiolife.sso.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.SendEmailCodeReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证控制器
 *
 * @author Lys
 * @date 2026/03/01
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    /**
     * 发送注册验证码
     *
     * @param body 邮箱参数
     */
    @PostMapping("/sendEmailCode")
    public ApiResponse<Void> sendEmailCode(@RequestBody SendEmailCodeReq body, HttpServletRequest request) {
        validateEmail(body);
        String ip = getIp(request);
        userService.sendRegisterCode(body.getEmail(), ip);
        return ApiResponse.success();
    }

    /**
     * 发送重置密码验证码
     *
     * @param body 邮箱参数
     */
    @PostMapping("/sendResetPasswordCode")
    public ApiResponse<Void> sendResetPasswordCode(@RequestBody SendEmailCodeReq body, HttpServletRequest request) {
        validateEmail(body);
        String ip = getIp(request);
        userService.sendResetPasswordCode(body.getEmail(), ip);
        return ApiResponse.success();
    }

    private void validateEmail(SendEmailCodeReq body) {
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 重置密码
     *
     * @param resetPasswordReq 重置密码参数
     */
    @PostMapping("/resetPassword")
    public ApiResponse<Void> resetPassword(@RequestBody ResetPasswordReq resetPasswordReq) {
        userService.resetPassword(resetPasswordReq);
        return ApiResponse.success();
    }

    /**
     * 注册
     *
     * @param registerReq 注册参数
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterReq registerReq) {
        userService.register(registerReq);
        return ApiResponse.success();
    }
}
