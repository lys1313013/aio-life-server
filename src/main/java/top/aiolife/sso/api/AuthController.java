package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.SendEmailCodeReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.service.AuthService;
import top.aiolife.sso.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 认证控制器
 *
 * @author Lys
 * @date 2026/03/01
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/sendEmailCode")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody SendEmailCodeReq body, HttpServletRequest request) {
        requireEmail(body);
        userService.sendRegisterCode(body.getEmail(), AuthService.clientIp(request));
        return ApiResponse.success();
    }

    @Operation(summary = "发送密码重置邮箱验证码")
    @PostMapping("/sendResetPasswordCode")
    public ApiResponse<Void> sendResetPasswordCode(@Valid @RequestBody SendEmailCodeReq body, HttpServletRequest request) {
        requireEmail(body);
        userService.sendResetPasswordCode(body.getEmail(), AuthService.clientIp(request));
        return ApiResponse.success();
    }

    /** 缺少邮箱时不触发验证码发送。 */
    private void requireEmail(SendEmailCodeReq body) {
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
    }

    @Operation(summary = "通过邮箱验证码重置登录密码")
    @PostMapping("/resetPassword")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordReq resetPasswordReq) {
        userService.resetPassword(resetPasswordReq);
        return ApiResponse.success();
    }

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterReq registerReq) {
        userService.register(registerReq);
        return ApiResponse.success();
    }
}
