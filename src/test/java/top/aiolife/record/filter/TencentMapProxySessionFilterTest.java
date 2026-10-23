package top.aiolife.record.filter;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.aiolife.record.notification.NotificationCryptoService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TencentMapProxySessionFilterTest {
    private final NotificationCryptoService crypto = new NotificationCryptoService("synthetic-test-master");
    private final TencentMapProxySessionFilter filter = new TencentMapProxySessionFilter(crypto);

    @Test void 签发限域HttpOnlyCookie且不明文保存登录凭据() throws Exception {
        var request = request(TencentMapProxySessionFilter.PATH); request.addHeader("X-Forwarded-Proto", "https");
        var response = new MockHttpServletResponse();
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("synthetic-token");
            filter.issue(request, response);
        }
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie); assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/api" + TencentMapProxySessionFilter.PATH));
        assertFalse(cookie.contains("synthetic-token"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        request.setCookies(new Cookie(TencentMapProxySessionFilter.COOKIE, cookie.split(";", 2)[0].split("=", 2)[1]));
        var chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        var wrapped = ArgumentCaptor.forClass(HttpServletRequest.class); verify(chain).doFilter(wrapped.capture(), any());
        assertEquals("Bearer synthetic-token", wrapped.getValue().getHeader("Authorization"));
    }

    @Test void 过期损坏异用途Cookie和非代理路径均不能建立身份() throws Exception {
        for (String value : new String[]{"invalid", crypto.encrypt("tencent-map-proxy:1\nsynthetic-token"),
                crypto.encrypt("different-purpose:" + Instant.now().plusSeconds(60).getEpochSecond() + "\nsynthetic-token")}) {
            var request = request(TencentMapProxySessionFilter.PATH + "/checkKey");
            request.setCookies(new Cookie(TencentMapProxySessionFilter.COOKIE, value));
            var chain = mock(FilterChain.class); filter.doFilter(request, new MockHttpServletResponse(), chain);
            verify(chain).doFilter(same(request), any()); assertNull(request.getHeader("Authorization"));
        }
        var request = request("/life-footprints");
        request.setCookies(validCookie());
        var chain = mock(FilterChain.class); filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(same(request), any());
    }

    @Test void 显式Authorization优先于代理Cookie() throws Exception {
        var request = request(TencentMapProxySessionFilter.PATH);
        request.setCookies(validCookie()); request.addHeader("Authorization", "Bearer explicit");
        var chain = mock(FilterChain.class); filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(same(request), any());
        assertEquals("Bearer explicit", request.getHeader("Authorization"));
    }

    private Cookie validCookie() {
        return new Cookie(TencentMapProxySessionFilter.COOKIE, crypto.encrypt("tencent-map-proxy:"
                + Instant.now().plusSeconds(60).getEpochSecond() + "\nsynthetic-token"));
    }
    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", "/api" + path);
        request.setContextPath("/api"); return request;
    }
}
