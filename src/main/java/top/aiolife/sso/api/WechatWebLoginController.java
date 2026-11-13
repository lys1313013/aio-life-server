package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.service.AuthService;
import top.aiolife.sso.wechat.WechatWebLoginService;

import java.util.Map;

/**
 * 微信网页扫码登录控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "微信网页登录")
@RestController
@RequestMapping("/auth/wechat/web")
@RequiredArgsConstructor
public class WechatWebLoginController {
    public static final String[] PUBLIC_PATHS = {"/auth/wechat/web/capabilities", "/auth/wechat/web/create",
            "/auth/wechat/web/status", "/auth/wechat/web/exchange", "/auth/wechat/web/revoke"};
    private final WechatWebLoginService service;

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @Operation(summary = "查询微信网页登录能力")
    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Boolean>> capabilities() { return ApiResponse.success(Map.of("enabled", service.enabled())); }

    @Operation(summary = "创建微信网页登录二维码")
    @PostMapping("/create")
    public ApiResponse<WechatWebLoginService.Challenge> create(HttpServletRequest request) {
        return ApiResponse.success(service.create(request.getRemoteAddr()));
    }

    @Operation(summary = "查询微信网页登录状态")
    @PostMapping("/status")
    public ApiResponse<Map<String, String>> status(@Valid @RequestBody BrowserRequest body) {
        return state(service.status(body.scene(), body.browserSecret()));
    }

    @Operation(summary = "兑换微信网页登录凭据")
    @PostMapping("/exchange")
    public ApiResponse<UserLoginVO> exchange(@Valid @RequestBody BrowserRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.exchange(body.scene(), body.browserSecret(), request.getRemoteAddr()));
    }

    @Operation(summary = "撤销微信网页登录二维码")
    @PostMapping("/revoke")
    public ApiResponse<Void> revoke(@Valid @RequestBody BrowserRequest body) {
        service.revoke(body.scene(), body.browserSecret());
        return ApiResponse.success();
    }

    @Operation(summary = "扫描微信网页登录二维码")
    @PostMapping("/scan")
    public ApiResponse<Map<String, String>> scan(@CurrentUserId String userId, @Valid @RequestBody ScanRequest body) {
        AuthService.requireAccountSession();
        return state(service.scan(body.scene(), IdCodec.decode(userId)));
    }

    @Operation(summary = "确认微信网页登录")
    @PostMapping("/confirm")
    public ApiResponse<Map<String, String>> confirm(@CurrentUserId String userId,
                                                    @Valid @RequestBody ConfirmRequest body,
                                                    HttpServletRequest request) {
        AuthService.requireAccountSession();
        return state(service.confirm(body.scene(), IdCodec.decode(userId), body.loginCode(), request.getRemoteAddr()));
    }

    @Operation(summary = "拒绝微信网页登录")
    @PostMapping("/cancel")
    public ApiResponse<Map<String, String>> cancel(@CurrentUserId String userId, @Valid @RequestBody ScanRequest body) {
        AuthService.requireAccountSession();
        return state(service.cancel(body.scene(), IdCodec.decode(userId)));
    }

    private ApiResponse<Map<String, String>> state(String value) { return ApiResponse.success(Map.of("status", value)); }

    public record BrowserRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene,
                                 @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String browserSecret) {}
    public record ScanRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene) {}
    public record ConfirmRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene,
                                 @NotBlank @Size(max = 256) String loginCode) {}
}
