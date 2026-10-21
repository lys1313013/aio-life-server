package top.aiolife.system.aop;

import java.time.LocalDateTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.util.RequestLoginContext;
import top.aiolife.system.filter.ActivityLogTimingFilter;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogParameters;
import top.aiolife.system.service.ActivityLogRecorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityLogAspectTest {
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final ActivityLogParameters parameters = mock(ActivityLogParameters.class);
    private final ActivityLogAspect aspect = new ActivityLogAspect(recorder, parameters);

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
            when(point.proceed()).thenReturn(ApiResponse.error(1, "失败"));
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
    @Test
    void operation_采集方法时间和实例地址且耗时不包含落库() throws Throwable {
        var point = request("/tasks/13", "update");
        var request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        var time = LocalDateTime.of(2026, 10, 21, 12, 0, 0, 123000000);
        request.setAttribute(ActivityLogTimingFilter.START_TIME, time);
        request.setAttribute(ActivityLogTimingFilter.START_NANOS, System.nanoTime() - 10_000_000);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/tasks/{id}");
        when(parameters.serverIp()).thenReturn("2001:db8::1");
        when(parameters.capture(any(), any(), any())).thenReturn("{\"path\":{\"id\":\"13\"}}");
        try (var auth = mockStatic(RequestLoginContext.class)) {
            auth.when(RequestLoginContext::userIdOrNull).thenReturn(13L);
            when(point.proceed()).thenReturn(ApiResponse.success());
            aspect.around(point);
            var row = recorded();
            assertEquals(time, row.getRequestTime());
            assertEquals("POST", row.getRequestMethod());
            assertEquals("/tasks/{id}", row.getRequestPath());
            assertEquals("2001:db8::1", row.getServerIp());
            assertTrue(row.getDurationMs() >= 10);
            assertTrue(row.getRequestParams().contains("13"));
        }
    }

    @Test
    void operation_异常仍记录且参数采集故障不改变异常() throws Throwable {
        var point = request("/tasks", "create");
        when(parameters.serverIp()).thenThrow(new IllegalStateException("metadata unavailable"));
        var failure = new IllegalArgumentException("business failure");
        when(point.proceed()).thenThrow(failure);
        try (var auth = mockStatic(RequestLoginContext.class)) {
            auth.when(RequestLoginContext::userIdOrNull).thenReturn(13L);
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> aspect.around(point)));
            assertFalse(recorded().getSuccess());
            assertNotNull(recorded().getDurationMs());
        }
    }

}
