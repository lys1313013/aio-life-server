package top.aiolife.sso.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 认证动作集中入口，Controller 不直接调用 Sa-Token。
 *
 * @author Lys
 * @date 2026/11/13
 */
public final class AuthService {

    private AuthService() {
    }

    /** 退出当前登录会话。 */
    public static void logout() {
        StpUtil.logout();
    }

    /** API Key 或切换会话不能执行需要本人账号的操作。 */
    public static void requireAccountSession() {
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用账号登录后操作");
        }
    }

    /** 扫码绑定只接受真实 Bearer 登录会话，不能使用 API Key 或切换身份。 */
    public static void requireScanSession(String authorization, String token) {
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH")) || StpUtil.isSwitch()
                || authorization == null || !authorization.startsWith("Bearer ")
                || token == null || !authorization.substring(7).equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用已登录的 App 或小程序扫码");
        }
    }

    /** 读取客户端 IP，兼容代理转发头。 */
    public static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
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
