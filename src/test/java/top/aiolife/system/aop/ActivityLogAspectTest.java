package top.aiolife.system.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.util.RequestLoginContext;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogRecorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityLogAspectTest {
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final ActivityLogAspect aspect = new ActivityLogAspect(recorder);

    @AfterEach
    void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    private ProceedingJoinPoint request(String path, String name) {
        var request = new MockHttpServletRequest("POST", "/api" + path);
        request.setContextPath("/api");
        request.addHeader("X-Page-Path", "/system/user");
        request.addHeader("User-Agent", "test-browser");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var point = mock(ProceedingJoinPoint.class);
        var signature = mock(Signature.class);
        when(signature.getName()).thenReturn(name);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[0]);
        return point;
    }

    private ActivityLogEntity recorded() {
        var captor = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(recorder).record(captor.capture(), eq("/system/user"), eq("test-browser"));
        return captor.getValue();
    }

    @Test
    void login_成功使用返回身份() throws Throwable {
        var point = request("/auth/login", "login");
        var input = new LoginReq(); input.setUsername("alice"); input.setPassword("not-for-logs");
        when(point.getArgs()).thenReturn(new Object[]{input});
        var user = new UserLoginVO(); user.setId(13L);
        var result = ApiResponse.success(user);
        when(point.proceed()).thenReturn(result);
        assertSame(result, aspect.around(point));
        var row = recorded();
        assertEquals(13L, row.getUserId());
        assertEquals("alice", row.getUsername());
        assertEquals("ACCESS", row.getLogType());
        assertEquals("账密登录", row.getAccessType());
        assertTrue(row.getSuccess());
    }

    @Test
    void login_失败保留尝试账号且原异常不变() throws Throwable {
        var point = request("/auth/login", "login");
        var input = new LoginReq(); input.setUsername("missing"); input.setPassword("secret");
        when(point.getArgs()).thenReturn(new Object[]{input});
        var error = new IllegalArgumentException("bad credentials");
        when(point.proceed()).thenThrow(error);
        assertSame(error, assertThrows(IllegalArgumentException.class, () -> aspect.around(point)));
        assertFalse(recorded().getSuccess());
    }

    @Test
    void logout_注销后仍保留原用户() throws Throwable {
        var point = request("/auth/logout", "logout");
        try (var auth = mockStatic(RequestLoginContext.class)) {
            auth.when(RequestLoginContext::userIdOrNull).thenReturn(13L);
            when(point.proceed()).thenAnswer(inv -> {
                auth.when(RequestLoginContext::userIdOrNull).thenReturn(null);
                return ApiResponse.success();
            });
            aspect.around(point);
            assertEquals(13L, recorded().getUserId());
            assertEquals("登出", recorded().getAccessType());
        }
    }

    @Test
    void operation_业务失败码不是成功() throws Throwable {
        var point = request("/users", "modify");
        try (var auth = mockStatic(RequestLoginContext.class)) {
            auth.when(RequestLoginContext::userIdOrNull).thenReturn(13L);
            when(point.proceed()).thenReturn(ApiResponse.error("1", "失败"));
            aspect.around(point);
            assertFalse(recorded().getSuccess());
            assertEquals("修改", recorded().getFunctionItem());
        }
    }

    @Test
    void recording_写入失败不覆盖业务结果() throws Throwable {
        var point = request("/auth/login", "login");
        var result = ApiResponse.success();
        when(point.proceed()).thenReturn(result);
        doThrow(new IllegalStateException("db unavailable")).when(recorder).record(any(), any(), any());
        assertSame(result, aspect.around(point));
        verify(point, times(1)).proceed();
    }

    @Test
    void publicRequest_不采集未登录非登录请求() throws Throwable {
        var point = request("/auth/register", "register");
        try (var auth = mockStatic(RequestLoginContext.class)) {
            auth.when(RequestLoginContext::userIdOrNull).thenReturn(null);
            aspect.around(point);
            verifyNoInteractions(recorder);
            verify(point).proceed();
        }
    }
}
