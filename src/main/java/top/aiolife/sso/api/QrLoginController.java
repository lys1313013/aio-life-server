package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentToken;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.qr.QrLoginService;
import top.aiolife.sso.service.AuthService;

import static top.aiolife.sso.qr.QrLoginModels.*;

/**
 * 扫码登录控制器。浏览器入口使用独立票据鉴权；扫码和确认只接受真实 Bearer 登录会话。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "扫码登录")
@RestController
@RequestMapping("/auth/qr-login")
@RequiredArgsConstructor
public class QrLoginController {
    public static final String[] PUBLIC_PATHS = {"/auth/qr-login", "/auth/qr-login/*/status",
            "/auth/qr-login/consume", "/auth/qr-login/cancel"};
    private final QrLoginService service;

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @Operation(summary = "创建扫码登录二维码")
    @PostMapping
    public ApiResponse<Created> create(HttpServletRequest request) {
        return ApiResponse.success(service.create(request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @Operation(summary = "查询扫码登录状态")
    @GetMapping("/{id}/status")
    public ApiResponse<Status> status(@PathVariable String id, @RequestHeader("X-QR-Secret") String secret) {
        return ApiResponse.success(service.status(id, secret));
    }

    @Operation(summary = "扫描登录二维码")
    @PostMapping("/scan")
    public ApiResponse<Scanned> scan(@CurrentUserId String userId, @CurrentToken String token,
                                     @Valid @RequestBody ScanRequest body, HttpServletRequest request) {
        AuthService.requireScanSession(request.getHeader("Authorization"), token);
        return ApiResponse.success(service.scan(body, IdCodec.decode(userId), token));
    }

    @Operation(summary = "确认或拒绝扫码登录")
    @PostMapping("/decision")
    public ApiResponse<Status> decision(@CurrentUserId String userId, @CurrentToken String token,
                                        @Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
        AuthService.requireScanSession(request.getHeader("Authorization"), token);
        return ApiResponse.success(service.decide(body, IdCodec.decode(userId), token));
    }

    @Operation(summary = "兑换扫码登录凭据")
    @PostMapping("/consume")
    public ApiResponse<UserLoginVO> consume(@Valid @RequestBody BrowserRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.consume(body, request.getRemoteAddr()));
    }

    @Operation(summary = "取消扫码登录")
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody BrowserRequest body) {
        service.cancel(body);
        return ApiResponse.success();
    }
}
