package top.aiolife.sso.api;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.qr.QrLoginService;

import static top.aiolife.sso.qr.QrLoginModels.*;

/** 浏览器入口使用独立票据鉴权；扫码和确认只接受真实 Bearer 登录会话。 */
@RestController
@RequestMapping("/auth/qr-login")
@RequiredArgsConstructor
public class QrLoginController {
    public static final String[] PUBLIC_PATHS = {"/auth/qr-login", "/auth/qr-login/*/status",
            "/auth/qr-login/consume", "/auth/qr-login/cancel"};
    private final QrLoginService service;

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @PostMapping
    public ApiResponse<Created> create(HttpServletRequest request) {
        return ApiResponse.success(service.create(request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<Status> status(@PathVariable String id, @RequestHeader("X-QR-Secret") String secret) {
        return ApiResponse.success(service.status(id, secret));
    }

    @PostMapping("/scan")
    public ApiResponse<Scanned> scan(@Valid @RequestBody ScanRequest body, HttpServletRequest request) {
        long id = requireSession(request);
        return ApiResponse.success(service.scan(body, id, StpUtil.getTokenValue()));
    }

    @PostMapping("/decision")
    public ApiResponse<Status> decision(@Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
        long id = requireSession(request);
        return ApiResponse.success(service.decide(body, id, StpUtil.getTokenValue()));
    }

    @PostMapping("/consume")
    public ApiResponse<UserLoginVO> consume(@Valid @RequestBody BrowserRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.consume(body, request.getRemoteAddr()));
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@Valid @RequestBody BrowserRequest body) {
        service.cancel(body);
        return ApiResponse.success();
    }

    private long requireSession(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH")) || StpUtil.isSwitch()
                || authorization == null || !authorization.startsWith("Bearer ")
                || !authorization.substring(7).equals(StpUtil.getTokenValue())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用已登录的 App 或小程序扫码");
        }
        return StpUtil.getLoginIdAsLong();
    }
}
