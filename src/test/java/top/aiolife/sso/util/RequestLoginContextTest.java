package top.aiolife.sso.util;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestLoginContextTest {
    @AfterEach
    void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    private void newRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    void testUserId_只复用当前请求验证结果下次仍需认证() {
        try (var stp = mockStatic(StpUtil.class)) {
            newRequest();
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            RequestLoginContext.checkLogin();
            assertEquals(7L, RequestLoginContext.requireUserId());
            assertEquals(7L, RequestLoginContext.userIdOrNull());
            stp.verify(StpUtil::getLoginIdAsLong, times(1));
            newRequest();
            stp.when(StpUtil::getLoginIdAsLong).thenThrow(new IllegalStateException("expired"));
            assertThrows(IllegalStateException.class, RequestLoginContext::checkLogin);
            assertThrows(IllegalStateException.class, RequestLoginContext::requireUserId);
        }
    }

    @Test
    void testUserId_APIKey临时身份不被缓存身份覆盖() {
        try (var stp = mockStatic(StpUtil.class)) {
            newRequest();
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            RequestLoginContext.checkLogin();
            stp.when(StpUtil::isSwitch).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
            stp.when(StpUtil::getLoginIdDefaultNull).thenReturn(8L);
            assertEquals(8L, RequestLoginContext.requireUserId());
            assertEquals(8L, RequestLoginContext.userIdOrNull());
        }
    }

    @Test
    void testUserId_未鉴权不会自动放行() {
        try (var stp = mockStatic(StpUtil.class)) {
            newRequest();
            stp.when(StpUtil::getLoginIdAsLong).thenThrow(new IllegalStateException("unauthorized"));
            assertNull(RequestLoginContext.userIdOrNull());
            assertThrows(IllegalStateException.class, RequestLoginContext::requireUserId);
        }
    }
}
