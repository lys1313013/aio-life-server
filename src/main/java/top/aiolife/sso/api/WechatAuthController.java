package top.aiolife.sso.api;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.wechat.*;

import java.util.Map;

/** 单个微信小程序的认证入口，App / Web 不使用这些凭证交换接口。 */
@RestController
@RequestMapping("/auth/wechat/mini")
@RequiredArgsConstructor
public class WechatAuthController {
    public static final String[] PUBLIC_PATHS = {"/auth/wechat/mini/capabilities",
            "/auth/wechat/mini/login", "/auth/wechat/mini/phone-login"};
    private final WechatAuthService service;
    private final WechatMiniClient client;

    /** 登录页按服务端配置展示入口，不暴露 AppSecret。 */
    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Boolean>> capabilities() {
        return ApiResponse.success(Map.of("enabled", client.enabled()));
    }

    /** 校验 loginCode，已绑定则登录，否则返回五分钟临时票据。 */
    @PostMapping("/login")
    public ApiResponse<WechatLoginVO> login(@Valid @RequestBody WechatAuthRequests.Login body, HttpServletRequest request) {
        return ApiResponse.success(service.login(body.loginCode(), request.getRemoteAddr()));
    }

    /** 用户主动授权手机号后注册；已占用号码进入原账号验证。 */
    @PostMapping("/phone-login")
    public ApiResponse<WechatLoginVO> phoneLogin(@Valid @RequestBody WechatAuthRequests.PhoneLogin body, HttpServletRequest request) {
        return ApiResponse.success(service.phoneLogin(body, request.getRemoteAddr()));
    }

    /** 使用当前业务会话及原账号密码，绑定已验证的微信身份。 */
    @PostMapping("/bind")
    public ApiResponse<WechatLoginVO> bind(@Valid @RequestBody WechatAuthRequests.Bind body, HttpServletRequest request) {
        return ApiResponse.success(service.bind(requireSession(), body, request.getRemoteAddr()));
    }

    /** 无密码账号通过新鲜微信凭证首次设置密码，不能覆盖已有密码。 */
    @PostMapping("/password")
    public ApiResponse<Void> initializePassword(@Valid @RequestBody WechatAuthRequests.InitializePassword body, HttpServletRequest request) {
        service.initializePassword(requireSession(), body, request.getRemoteAddr());
        return ApiResponse.success();
    }

    private long requireSession() {
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用账号登录后操作");
        }
        return StpUtil.getLoginIdAsLong();
    }
}
