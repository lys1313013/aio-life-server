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

@RestController
@RequiredArgsConstructor
public class QueryAccessController {
    public static final String INTERNAL_EVALUATE_PATH = "/internal/query/access/evaluate";
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
}
