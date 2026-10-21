package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.api.FootprintMapController;
import top.aiolife.record.config.FootprintMapProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FootprintMapClientTest {
    private FootprintMapProperties properties;
    private HttpClient http;
    private HttpResponse<String> response;
    private FootprintMapClient client;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        properties = new FootprintMapProperties(); properties.setBaiduServiceAk("synthetic-server-key"); properties.setBaiduBrowserAk("synthetic-browser-key");
        http = mock(HttpClient.class); response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        client = new FootprintMapClient(properties, new ObjectMapper(), http);
    }

    @Test void testSearch_使用国测局坐标过滤缺失坐标且不混淆经纬度() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"results":[{"name":"住所","address":"地址","city":"杭州","uid":"poi1","location":{"lng":120.1,"lat":30.2}},{"name":"无坐标"}]}
            """);
        var found = client.search(1, "住所", "全国");
        assertEquals(1, found.size()); assertEquals(120.1, found.getFirst().longitude()); assertEquals(30.2, found.getFirst().latitude());
        assertEquals("GCJ02", found.getFirst().coordinateSystem());
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertEquals("api.map.baidu.com", request.getValue().uri().getHost());
        assertEquals("/place/v2/search", request.getValue().uri().getPath());
        assertTrue(request.getValue().uri().getQuery().contains("ret_coordtype=gcj02ll"));
    }

    @Test void testGeocode_超长地址不发送到上游() throws Exception {
        var error = assertThrows(ResponseStatusException.class, () -> client.geocode(1, "址".repeat(43)));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test void testFailure_HTTP成功不代表业务成功或存在有效坐标() {
        for (String body : new String[]{"{\"status\":302}", "{\"results\":[]}", "not json", "{\"status\":0,\"result\":{}}"}) {
            when(response.body()).thenReturn(body);
            assertThrows(ResponseStatusException.class, () -> client.geocode(1, "示例地址"));
        }
        properties.setBaiduServiceAk("");
        assertFalse(client.available());
        assertThrows(ResponseStatusException.class, () -> client.search(1, "住所", "全国"));
    }

    @Test void testReverse_上游描述不能移动手工标点且只下发浏览器配置() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"result":{"formatted_address":"示例地址","location":{"lng":121,"lat":31},"addressComponent":{"city":"杭州"}}}
            """);
        var result = client.reverse(1, 120.1, 30.2);
        assertEquals(120.1, result.longitude()); assertEquals(30.2, result.latitude());
        var config = new FootprintMapController(client).config().getData();
        var json = new ObjectMapper().writeValueAsString(config);
        assertTrue(json.contains("synthetic-browser-key")); assertFalse(json.contains("synthetic-server-key"));
    }
}
