package top.aiolife.record.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.service.FootprintTencentConfigService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TencentMapProxyClientTest {
    private final FootprintTencentConfigService config = mock(FootprintTencentConfigService.class);
    private final HttpClient http = mock(HttpClient.class);
    private HttpResponse<byte[]> response;
    private TencentMapProxyClient client;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        when(config.displayKey()).thenReturn("synthetic-server-key");
        response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/javascript;charset=UTF-8")), (a, b) -> true));
        when(response.body()).thenReturn("TMap._svcb.test({\"info\":{\"error\":0},\"key\":\"synthetic-server-key\"})".getBytes(StandardCharsets.UTF_8));
        client = new TencentMapProxyClient(config, http);
    }

    @Test void 固定上游补齐Key且响应不泄漏凭据或上游响应头() throws Exception {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("cb", "TMap._svcb.test"); parameters.add("pid", "localhost:5666");
        parameters.add("key", "attacker-key"); parameters.add("KEY", "another-key"); parameters.add("key2", "second-key");
        var result = client.forward(7L, "/checkKey", parameters);
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        var request = captor.getValue();
        assertEquals("apikey.map.qq.com", request.uri().getHost());
        assertEquals("/mkey/index.php/mkey/check", request.uri().getPath());
        assertTrue(request.uri().getQuery().contains("key=synthetic-server-key"));
        assertFalse(request.uri().getQuery().contains("attacker"));
        assertFalse(request.uri().getQuery().contains("key2"));
        assertTrue(request.headers().map().isEmpty());
        String body = new String(result.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("TMap._svcb.test("));
        assertFalse(body.contains("synthetic-server-key"));
        assertEquals("no-store", result.getHeaders().getCacheControl());
        assertFalse(result.getHeaders().containsKey("Set-Cookie"));
    }

    @Test void 统计与海外路由按官方映射且兼容海外异常ContentType() throws Exception {
        client.forward(7, "", new LinkedMultiValueMap<>());
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json;text/html;charset=UTF-8")), (a, b) -> true));
        client.forward(7, "/oversea", new LinkedMultiValueMap<>());
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any());
        assertEquals("https://pr.map.qq.com/pingd?appid=jsapi_v3&key=synthetic-server-key", captor.getAllValues().getFirst().uri().toString());
        assertEquals("https://overseactrl.map.qq.com/?apikey=synthetic-server-key", captor.getAllValues().getLast().uri().toString());
    }

    @Test void 拒绝任意上游路径回调注入以及超长参数() throws Exception {
        for (String path : List.of("/service/geocoder/v1", "/checkKey/extra", "https://evil.test", "/../checkKey"))
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> client.forward(7, path, new LinkedMultiValueMap<>())).getStatusCode().value());
        var parameters = new LinkedMultiValueMap<String, String>(); parameters.add("cb", "alert(1)//");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> client.forward(7, "/checkKey", parameters)).getStatusCode().value());
        parameters.clear(); parameters.add("pid", "x".repeat(4097));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> client.forward(7, "/checkKey", parameters)).getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test void 不把HTTP成功当业务成功且拒绝重定向和脱敏传输异常() throws Exception {
        when(response.body()).thenReturn("TMap._svcb.test({\"info\":{\"error\":-202}})".getBytes(StandardCharsets.UTF_8));
        assertTrue(new String(client.forward(7, "/checkKey", new LinkedMultiValueMap<>()).getBody(), StandardCharsets.UTF_8).contains("-202"));
        when(response.statusCode()).thenReturn(302);
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> client.forward(7, "/checkKey", new LinkedMultiValueMap<>())).getStatusCode().value());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("https://upstream?key=synthetic-server-key"));
        var error = assertThrows(ResponseStatusException.class, () -> client.forward(7, "/checkKey", new LinkedMultiValueMap<>()));
        assertFalse(error.toString().contains("synthetic-server-key")); assertNull(error.getCause());
    }

    @Test void 未配置及频率超限均不调用上游() throws Exception {
        when(config.displayKey()).thenReturn("");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> client.forward(7, "", new LinkedMultiValueMap<>())).getStatusCode().value());
        verifyNoInteractions(http);
        when(config.displayKey()).thenReturn("synthetic-server-key");
        for (int i = 0; i < 239; i++) client.forward(7, "", new LinkedMultiValueMap<>());
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> client.forward(7, "", new LinkedMultiValueMap<>())).getStatusCode().value());
        client.forward(8, "", new LinkedMultiValueMap<>());
        verify(http, times(240)).send(any(), any());
    }
}
