package top.aiolife.sso.interceptor;

import top.aiolife.sso.util.RequestLoginContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.util.RedisUtil;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 二级锁拦截器，在 SaInterceptor 之后执行，校验二级密码验证状态。
 *
 * @author Lys
 * @date 2026/07/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondaryLockInterceptor implements HandlerInterceptor {

    private static final String UNLOCK_KEY_PREFIX = "secondary:unlock:";
    private static final String FAIL_COUNT_KEY_PREFIX = "secondary:fail:";
    private static final long UNLOCK_TTL_MINUTES = 30;
    private static final int MAX_FAIL_COUNT = 5;
    private static final long FAIL_LOCK_MINUTES = 10;

    private final SecondaryLockMenuCache menuCache;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 未登录则跳过
        Long userId = RequestLoginContext.userIdOrNull();
        if (userId == null) {
            return true;
        }

        var parsed = org.springframework.web.util.ServletRequestPathUtils.hasParsedRequestPath(request)
                ? org.springframework.web.util.ServletRequestPathUtils.getParsedRequestPath(request)
                : org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        String path = parsed.pathWithinApplication().elements().stream()
                .map(element -> element instanceof org.springframework.http.server.PathContainer.PathSegment segment
                        ? segment.valueToMatch() : element.value())
                .collect(java.util.stream.Collectors.joining());
        if (request.getContextPath().isEmpty() && path.startsWith("/api/")) path = path.substring(4);
        String lockedMenu = menuCache.findMatchedPaths(userId, path).stream()
                .filter(menu -> !redisUtil.hasKey(unlockKey(userId, menu))).findFirst().orElse(null);
        if (lockedMenu == null) return true;

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> resp = ApiResponse.error(ResponseCodeConst.SECONDARY_LOCK_REQUIRED,
                "需要二级密码验证", java.util.Map.of("menuPath", lockedMenu));
        response.getWriter().write(objectMapper.writeValueAsString(resp));
        return false;
    }

    // ── 以下为 static 工具方法，供 Controller 层调用 ──

    public static String unlockKey(long userId, String menuPath) {
        return UNLOCK_KEY_PREFIX + userId + ":" + menuPath;
    }

    public static String failCountKey(long userId) {
        return FAIL_COUNT_KEY_PREFIX + userId;
    }

    public static long unlockTtlSeconds() {
        return TimeUnit.MINUTES.toSeconds(UNLOCK_TTL_MINUTES);
    }

    public static int maxFailCount() {
        return MAX_FAIL_COUNT;
    }

    public static long failLockMinutes() {
        return FAIL_LOCK_MINUTES;
    }
}
