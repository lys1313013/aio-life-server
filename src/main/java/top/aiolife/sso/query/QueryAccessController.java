package top.aiolife.sso.query;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.util.RequestLoginContext;

@RestController
@RequiredArgsConstructor
public class QueryAccessController {
    public static final String INTERNAL_EVALUATE_PATH = "/internal/query/access/evaluate";
    public static final String INTERNAL_CHECK_TOKEN_PATH = "/internal/query/access/check-token";
    private final QueryAccessService service;

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @PostMapping("/query/access-token")
    public ApiResponse<QueryAccessModels.Issued> issue(@Valid @RequestBody QueryAccessModels.IssueRequest body,
                                                     HttpServletRequest request) {
        long userId = requireSession(request);
        return ApiResponse.success(service.issue(userId, StpUtil.getTokenValue(), body.appId()));
    }

    @DeleteMapping("/query/grants/{id}")
    public ApiResponse<Void> revoke(@PathVariable String id, HttpServletRequest request) {
        service.revoke(requireSession(request), id);
        return ApiResponse.success();
    }

    @Hidden
    @PostMapping(INTERNAL_EVALUATE_PATH)
    public ApiResponse<QueryAccessModels.Decision> evaluate(
            @RequestHeader(value = "X-AIO-Query-Service-Key", required = false) String serviceKey,
            @Valid @RequestBody QueryAccessModels.EvaluateRequest body) {
        return ApiResponse.success(service.evaluate(serviceKey, body));
    }

    private long requireSession(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH")) || StpUtil.isSwitch()
                || authorization == null || !authorization.startsWith("Bearer ")
                || !authorization.substring(7).equals(StpUtil.getTokenValue())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用登录会话管理查询授权");
        }
        return StpUtil.getLoginIdAsLong();
    }

    // 不加入 SaTokenConfig 的排除列表：必须经过现有登录、API Key 和账号状态校验。
    @Hidden
    @PostMapping(INTERNAL_CHECK_TOKEN_PATH)
    public ApiResponse<QueryAccessModels.TokenDecision> checkToken(
            @RequestHeader(value = "X-AIO-Query-Service-Key", required = false) String serviceKey,
            @Valid @RequestBody QueryAccessModels.CheckTokenRequest body, HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        boolean apiKey = Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"));
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7
                || (!apiKey && (StpUtil.isSwitch() || !authorization.substring(7).equals(StpUtil.getTokenValue())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要显式 Bearer 凭据");
        }
        return ApiResponse.success(service.checkToken(serviceKey, RequestLoginContext.requireUserId(),
                authorization.substring(7), apiKey, body.dataset()));
    }
}
