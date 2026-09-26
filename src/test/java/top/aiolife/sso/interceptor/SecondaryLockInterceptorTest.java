package top.aiolife.sso.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.util.RequestLoginContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecondaryLockInterceptorTest {
    @Test
    void testLock_复用认证身份后仍检查菜单锁及解锁状态() throws Exception {
        var menu = mock(SecondaryLockMenuCache.class);
        var redis = mock(RedisUtil.class);
        var interceptor = new SecondaryLockInterceptor(menu, redis, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/timeRecord/recommendNext");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            RequestLoginContext.checkLogin();
            when(menu.findMatchedPaths(7L, "/timeRecord/recommendNext")).thenReturn(java.util.Set.of("/timeRecord"));
            when(redis.hasKey("secondary:unlock:7:/timeRecord")).thenReturn(false, true);
            var response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertTrue(response.getContentAsString().contains("需要二级密码验证"));
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
            stp.verify(StpUtil::getLoginIdAsLong, times(1));
            verify(redis, times(2)).hasKey("secondary:unlock:7:/timeRecord");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
