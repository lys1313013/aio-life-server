package top.aiolife.sso.interceptor;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.aiolife.core.annotation.SecondaryLock;
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
        if (!StpUtil.isLogin()) {
            return true;
        }

        String menuPath = resolveMenuPath(request, handler);
        if (menuPath == null) {
            return true;
        }

        long userId = StpUtil.getLoginIdAsLong();
        String unlockKey = UNLOCK_KEY_PREFIX + userId + ":" + menuPath;

        if (redisUtil.hasKey(unlockKey)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> resp = ApiResponse.error(ResponseCodeConst.SECONDARY_LOCK_REQUIRED, "需要二级密码验证");
        response.getWriter().write(objectMapper.writeValueAsString(resp));
        return false;
    }

    /**
     * 解析当前请求对应的二级锁菜单路径。
     */
    private String resolveMenuPath(HttpServletRequest request, Object handler) {
        // 优先检查 @SecondaryLock 注解
        if (handler instanceof HandlerMethod hm) {
            SecondaryLock ann = hm.getMethodAnnotation(SecondaryLock.class);
            if (ann == null) {
                ann = hm.getBeanType().getAnnotation(SecondaryLock.class);
            }
            if (ann != null) {
                // 注解标记的接口，用请求路径前缀匹配
                String path = request.getRequestURI();
                if (path.startsWith("/api/")) {
                    path = path.substring(4); // 去掉 /api context-path
                }
                return menuCache.findMatchedPath(path);
            }
        }

        // 非注解模式：通过请求路径匹配菜单
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }
        return menuCache.findMatchedPath(path);
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
