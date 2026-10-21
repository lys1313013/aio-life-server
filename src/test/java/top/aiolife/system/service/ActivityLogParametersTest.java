package top.aiolife.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerMapping;
import top.aiolife.system.filter.ActivityLogTimingFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityLogParametersTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ActivityLogParameters collector = new ActivityLogParameters(json);

    public void endpoint(@RequestBody Map<String, Object> body) {}

    private ProceedingJoinPoint point(Map<String, Object> body) throws Exception {
        var point = mock(ProceedingJoinPoint.class);
        var signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(getClass().getMethod("endpoint", Map.class));
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{body});
        return point;
    }

    @Test void 只读取白名单且保留路径大ID与重复查询值() throws Exception {
        var request = new MockHttpServletRequest("POST", "/tasks/9223372036854775806");
        request.addParameter("page", "1", "2"); request.addParameter("password", "secret");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "9223372036854775806"));
        var value = collector.capture(request, point(Map.of("columnId", 9223372036854775806L, "content", "private notes")), "/tasks/{id}");
        // 默认规则明确允许分页、路径 ID 和列 ID，不采集自由文本。
        var tree = json.readTree(value);
        assertEquals("9223372036854775806", tree.at("/body/columnId").asText());
        assertEquals("9223372036854775806", tree.at("/path/id").asText());
        assertEquals(2, tree.at("/query/page").size());
        assertFalse(value.contains("secret")); assertFalse(value.contains("private notes"));
    }

    @Test void 递归脱敏优先于白名单且清除URL内凭据() throws Exception {
        var body = Map.<String,Object>of("status", Map.of("Password", "p", "child", List.of(Map.of("api-key", "k")),
                "website", "https://user:pass@example.com/?token=secret"), "access_token", "never-store");
        String value = collector.capture(new MockHttpServletRequest(), point(body), "/tasks");
        var tree = json.readTree(value);
        assertEquals("[REDACTED]", tree.at("/body/status/Password").asText());
        assertEquals("[REDACTED]", tree.at("/body/status/child/0/api-key").asText());
        assertEquals("[REDACTED]", tree.at("/body/status/website").asText());
        assertFalse(value.contains("never-store")); assertFalse(value.contains("token=secret"));
    }

    @Test void 敏感及未知接口不采集参数() throws Exception {
        for (String path : List.of("/auth/login", "/password", "/bank-cards/12", "/userbinds", "/api-key", "/system-config", "/system/logs/operation/12")) {
            assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", "secret")), path));
        }
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", "secret")), "/unconfigured"));
    }

    @Test void 空参数与未获准采集参数不同且不读取请求流() throws Exception {
        assertEquals("{}", collector.capture(new MockHttpServletRequest(), point(null), "/tasks"));
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("content", "private")), "/tasks"));
        var request = spy(new MockHttpServletRequest());
        collector.capture(request, point(null), "/tasks");
        verify(request, never()).getInputStream(); verify(request, never()).getReader();
    }

    @Test void 大小深度节点限制均丢弃整个快照() throws Exception {
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", "x".repeat(2001))), "/tasks"));
        Object nested = "safe";
        for (int i = 0; i < 10; i++) nested = List.of(nested);
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", nested)), "/tasks"));
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", Collections.nCopies(1001, "x"))), "/tasks"));
        var many = new LinkedHashMap<String, Object>();
        for (int i=0; i<25; i++) many.put("value"+i, "中".repeat(1000));
        assertNull(collector.capture(new MockHttpServletRequest(), point(Map.of("status", many)), "/tasks"));
    }

    @Test void 表单正文与同名查询参数分组且重复值不丢失() throws Exception {
        var request = new MockHttpServletRequest("POST", "/tasks");
        request.setContentType("application/x-www-form-urlencoded");
        request.setQueryString("columnId=7&columnId=8");
        request.addParameter("columnId", "7", "8", "9", "10");
        var tree = json.readTree(collector.capture(request, point(null), "/tasks"));
        assertEquals("7",tree.at("/query/columnId/0").asText());
        assertEquals("8",tree.at("/query/columnId/1").asText());
        assertEquals("9",tree.at("/body/columnId/0").asText());
        assertEquals("10",tree.at("/body/columnId/1").asText());
    }

    @Test void 早期过滤器保留原请求并将时刻截断到毫秒避免数据库跨日舍入() throws Exception {
        var request = new MockHttpServletRequest("POST", "/tasks");
        byte[] original = "{\"columnId\":\"13\"}".getBytes(StandardCharsets.UTF_8);
        request.setContent(original);
        new ActivityLogTimingFilter().doFilter(request,
                new MockHttpServletResponse(), (req,res) -> {
                    assertNotNull(req.getAttribute(ActivityLogTimingFilter.START_NANOS));
                    var time = (LocalDateTime) req.getAttribute(ActivityLogTimingFilter.START_TIME);
                    assertEquals(0,time.getNano()%1_000_000);
                    assertArrayEquals(original,req.getInputStream().readAllBytes());
                });
    }

}
