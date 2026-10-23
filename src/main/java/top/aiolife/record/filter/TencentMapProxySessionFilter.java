package top.aiolife.record.filter;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import top.aiolife.record.notification.NotificationCryptoService;

/** SDK 的 JSONP/统计请求无法附加 Authorization，使用仅限代理路径的加密会话 Cookie。
 * 只补齐请求凭据，不跳过现有 Sa-Token、账号状态和二级锁检查；不向腾讯转发此 Cookie。
 */
@Component
@RequiredArgsConstructor
public class TencentMapProxySessionFilter extends OncePerRequestFilter {
    public static final String PATH = "/life-footprints/map/tencent-proxy";
    static final String COOKIE = "aio_tencent_proxy";
    private static final String PURPOSE = "tencent-map-proxy:";
    private static final Duration LIFETIME = Duration.ofDays(1);
    private final NotificationCryptoService crypto;

    public void issue(HttpServletRequest request, HttpServletResponse response) {
        String token = StpUtil.getTokenValue();
        if (!StringUtils.hasText(token) || StpUtil.isSwitch()) return;
        String value = crypto.encrypt(PURPOSE + Instant.now().plus(LIFETIME).getEpochSecond() + "\n" + token);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))
                .sameSite("Strict")
                .path(request.getContextPath() + PATH).maxAge(LIFETIME).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals(PATH) && !path.startsWith(PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readToken(request.getCookies());
        if (StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION)) || token == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) {
                return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? "Bearer " + token : super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                        ? Collections.enumeration(Collections.singleton("Bearer " + token)) : super.getHeaders(name);
            }
            @Override public Enumeration<String> getHeaderNames() {
                var names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
                names.add(HttpHeaders.AUTHORIZATION);
                return Collections.enumeration(names);
            }
        }, response);
    }

    private String readToken(Cookie[] cookies) {
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (!COOKIE.equals(cookie.getName()) || cookie.getValue().length() > 4096) continue;
            try {
                String value = crypto.decrypt(cookie.getValue());
                if (!value.startsWith(PURPOSE)) return null;
                int split = value.indexOf('\n');
                long expires = Long.parseLong(value.substring(PURPOSE.length(), split));
                if (expires <= Instant.now().getEpochSecond()) return null;
                return value.substring(split + 1);
            } catch (RuntimeException ignored) {
                // 损坏、伪造、过期 Cookie 均不能建立身份，交由正常鉴权拒绝。
                return null;
            }
        }
        return null;
    }
}
