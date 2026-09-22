package top.aiolife.sso.util;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 仅在当前 Servlet 请求内复用鉴权结果；每个新请求仍由 Sa-Token 完整校验。 */
public final class RequestLoginContext {
    private static final String USER_ID = RequestLoginContext.class.getName() + ".userId";

    private RequestLoginContext() {}

    /** 与 checkLogin 一样执行完整校验，并保存这次校验已经读取的用户 ID。 */
    public static void checkLogin() {
        long userId = StpUtil.getLoginIdAsLong();
        HttpServletRequest request = currentRequest();
        if (request != null) request.setAttribute(USER_ID, userId);
    }

    public static long requireUserId() {
        Long userId = verifiedUserId();
        return userId != null ? userId : StpUtil.getLoginIdAsLong();
    }

    public static Long userIdOrNull() {
        Long userId = verifiedUserId();
        if (userId != null) return userId;
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? null : Long.valueOf(loginId.toString());
    }

    private static Long verifiedUserId() {
        HttpServletRequest request = currentRequest();
        // 临时身份切换（包括 API Key 认证）优先使用 Sa-Token 当前身份。
        if (request == null || StpUtil.isSwitch()) return null;
        Object userId = request.getAttribute(USER_ID);
        return userId instanceof Long id ? id : null;
    }

    private static HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
