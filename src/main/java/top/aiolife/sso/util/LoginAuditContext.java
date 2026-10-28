package top.aiolife.sso.util;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** 仅当前请求的服务端审计标记，不保存凭证，也不接受客户端传入。 */
public final class LoginAuditContext {
    private static final String RESULT_REPLAYED = LoginAuditContext.class.getName() + ".resultReplayed";

    private LoginAuditContext() {}

    public static void markResultReplayed() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) attributes.setAttribute(RESULT_REPLAYED, true, RequestAttributes.SCOPE_REQUEST);
    }

    public static boolean isResultReplayed() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes != null && Boolean.TRUE.equals(
                attributes.getAttribute(RESULT_REPLAYED, RequestAttributes.SCOPE_REQUEST));
    }
}
