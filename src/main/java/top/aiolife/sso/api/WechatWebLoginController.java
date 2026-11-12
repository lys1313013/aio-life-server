package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.wechat.WechatWebLoginService;

import java.util.Map;

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
    public ApiResponse<Map<String, String>> scan(@Valid @RequestBody ScanRequest body) {
        return state(service.scan(body.scene(), requireSession()));
    }

    @Operation(summary = "确认微信网页登录")
    @PostMapping("/confirm")
    public ApiResponse<Map<String, String>> confirm(@Valid @RequestBody ConfirmRequest body, HttpServletRequest request) {
        return state(service.confirm(body.scene(), requireSession(), body.loginCode(), request.getRemoteAddr()));
    }

    @Operation(summary = "拒绝微信网页登录")
    @PostMapping("/cancel")
    public ApiResponse<Map<String, String>> cancel(@Valid @RequestBody ScanRequest body) {
        return state(service.cancel(body.scene(), requireSession()));
    }

    private ApiResponse<Map<String, String>> state(String value) { return ApiResponse.success(Map.of("status", value)); }

    private long requireSession() {
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用账号登录后操作");
        }
        return StpUtil.getLoginIdAsLong();
    }

    public record BrowserRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene,
                                 @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String browserSecret) {}
    public record ScanRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene) {}
    public record ConfirmRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{32}") String scene,
                                 @NotBlank @Size(max = 256) String loginCode) {}
}
