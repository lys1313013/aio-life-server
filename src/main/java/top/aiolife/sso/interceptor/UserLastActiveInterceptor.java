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
import top.aiolife.core.redis.RedisUtil;

/**
 * 记录用户最后活跃时间（Token 请求）：写 Redis，低频同步到 DB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLastActiveInterceptor implements HandlerInterceptor {

    private static final String LAST_ACTIVE_KEY_PREFIX = "user:last_active:";
    private static final String DIRTY_SET_KEY = "user:last_active:dirty";

    private final RedisUtil redisUtil;

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

            long userId = StpUtil.getLoginIdAsLong();
            String key = LAST_ACTIVE_KEY_PREFIX + userId;

            long now = System.currentTimeMillis();
            Long prev = parseLong(redisUtil.get(key));
            if (prev != null) {
                long thresholdMs = Math.max(0, thresholdSeconds) * 1000;
                if (thresholdMs > 0 && now - prev < thresholdMs) {
                    return;
                }
            }

            redisUtil.set(key, String.valueOf(now));
            redisUtil.sAdd(DIRTY_SET_KEY, String.valueOf(userId));
        } catch (Exception e) {
            // 记录活跃时间失败不应影响主流程
            log.debug("记录用户最后活跃时间失败", e);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}

