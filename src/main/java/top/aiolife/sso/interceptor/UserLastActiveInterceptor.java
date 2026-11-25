package top.aiolife.sso.interceptor;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.aiolife.sso.service.UserLastActiveStore;

/**
 * 记录用户最后活跃时间（Token 请求）：写 Redis，低频同步到 DB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLastActiveInterceptor implements HandlerInterceptor {

    private final UserLastActiveStore active;

    /**
     * 同一用户更新活跃时间的最小间隔（秒），避免过于频繁写 Redis / dirty set
     */
    @Value("${spring.user.last-active.threshold-seconds:60}")
    private long thresholdSeconds;

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex
    ) {
        try {
            // API Key 不计入
            if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"))) {
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            if (!StpUtil.isLogin()) {
                return;
            }

            active.record(StpUtil.getLoginIdAsString(), System.currentTimeMillis(),
                    Math.max(0, thresholdSeconds) * 1000);
        } catch (Exception e) {
            // 记录活跃时间失败不应影响主流程
            log.debug("记录用户最后活跃时间失败", e);
        }
    }
}
