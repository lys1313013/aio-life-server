package top.aiolife.record.client;

import top.aiolife.system.service.FootprintTencentConfigService;

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
import top.aiolife.system.service.FootprintMapConfigService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FootprintMapClientTest {
    private FootprintMapProperties properties;
    private FootprintMapConfigService config;
    private HttpClient http;
    private HttpResponse<String> response;
    private FootprintMapClient client;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        properties = new FootprintMapProperties();
        config = mock(FootprintMapConfigService.class);
        when(config.serviceKey()).thenReturn("synthetic-server-key");
        when(config.tileSource()).thenReturn(new FootprintMapConfigService.TileSource(
                FootprintMapConfigService.DEFAULT_TILE_URL, "© OpenStreetMap contributors", "https://www.openstreetmap.org/copyright", "WGS84"));
        when(config.searchEnabled()).thenReturn(true);
        http = mock(HttpClient.class); response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        client = new FootprintMapClient(properties, config, new ObjectMapper(), http);
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
        assertTrue(request.getValue().uri().getQuery().contains("ak=synthetic-server-key"));
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
        when(config.serviceKey()).thenReturn("");
        when(config.searchEnabled()).thenReturn(false);
        assertFalse(client.available());
        assertThrows(ResponseStatusException.class, () -> client.search(1, "住所", "全国"));
    }

    @Test void testSearch_配置修改后下一次请求使用新AK() throws Exception {
        when(response.body()).thenReturn("{\"status\":0,\"results\":[]}");
        client.search(1, "住所", "全国");
        when(config.serviceKey()).thenReturn("synthetic-replacement-key");
        client.search(1, "住所", "全国");
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertTrue(requests.getAllValues().getLast().uri().getQuery().contains("ak=synthetic-replacement-key"));
    }

    @Test void testReverse_上游描述不能移动手工标点且只下发浏览器配置() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"result":{"formatted_address":"示例地址","location":{"lng":121,"lat":31},"addressComponent":{"city":"杭州"}}}
            """);
        var result = client.reverse(1, 120.1, 30.2);
        assertEquals(120.1, result.longitude()); assertEquals(30.2, result.latitude());
        var config = new FootprintMapController(client, mock(FootprintTencentConfigService.class)).config().getData();
        var json = new ObjectMapper().writeValueAsString(config);
        assertTrue(json.contains("tile.openstreetmap.org")); assertFalse(json.contains("browserKey")); assertFalse(json.contains("synthetic-server-key"));
    }
}
