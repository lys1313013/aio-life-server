package top.aiolife.sso.interceptor;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.service.IApiKeyLogService;
import top.aiolife.sso.service.IApiKeyService;
import top.aiolife.sso.service.AccountStatusGuard;

import java.time.LocalDateTime;

/**
 * API Key 认证拦截器
 *
 * @author Lys
 * @date 2026/03/09
 */
@Component
@RequiredArgsConstructor
public class ApiKeyInterceptor implements HandlerInterceptor {

    private final IApiKeyService apiKeyService;
    private final IApiKeyLogService apiKeyLogService;
    private final AccountStatusGuard accountStatusGuard;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 1. 获取 Authorization 头
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isBlank(authHeader)) {
            return true;
        }

        // 2. 检查是否为 API Key 格式 (ak- 开头)
        String apiKeyStr = authHeader.replace("Bearer ", "").trim();
        if (!apiKeyStr.startsWith("ak-")) {
            return true;
        }

        // 3. 校验 API Key
        ApiKeyEntity apiKeyEntity = apiKeyService.getByApiKey(apiKeyStr);
        if (apiKeyEntity == null || Integer.valueOf(1).equals(apiKeyEntity.getIsDeleted())) {
            throw new NotLoginException("API Key 无效", "API_KEY", NotLoginException.INVALID_TOKEN);
        }

        // 4. 检查是否过期
        if (apiKeyEntity.getExpiredAt() != null && apiKeyEntity.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new NotLoginException("API Key 已过期", "API_KEY", NotLoginException.TOKEN_TIMEOUT);
        }

        accountStatusGuard.requireActive(apiKeyEntity.getUserId());

        // 5. 临时身份切换 (仅限本次请求上下文，不产生真实会话)
        StpUtil.switchTo(apiKeyEntity.getUserId());
        
        // 6. 将认证信息存入 SaStorage，以便后续 SaInterceptor 跳过校验
        SaHolder.getStorage().set("API_KEY_ID", apiKeyEntity.getId());
        SaHolder.getStorage().set("IS_API_KEY_AUTH", true);
        
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) throws Exception {
        Boolean isApiKeyAuth = (Boolean) SaHolder.getStorage().get("IS_API_KEY_AUTH");
        if (Boolean.TRUE.equals(isApiKeyAuth)) {
            Long apiKeyId = (Long) SaHolder.getStorage().get("API_KEY_ID");
            // 记录调用日志
            apiKeyLogService.log(
                    apiKeyId,
                    request.getRequestURI(),
                    request.getMethod(),
                    response.getStatus(),
                    getIpAddress(request)
            );
            // 本次请求结束，结束身份切换，保持会话清洁
            StpUtil.endSwitch();
        }
    }

    private String getIpAddress(HttpServletRequest request) {
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
}
