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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TencentFootprintMapProviderTest {
    private FootprintTencentConfigService config;
    private HttpClient http;
    private HttpResponse<String> response;
    private TencentFootprintMapProvider client;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() throws Exception {
        config = mock(FootprintTencentConfigService.class);
        when(config.serviceKey()).thenReturn("synthetic-server-key");
        when(config.searchEnabled()).thenReturn(true);
        when(config.timeoutMs()).thenReturn(5000);
        http = mock(HttpClient.class); response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        client = new TencentFootprintMapProvider(config, new ObjectMapper(), http);
    }

    @Test void testSearch_使用国测局坐标过滤缺失坐标且不混淆经纬度() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"data":[{"title":"住所","address":"地址","city":"杭州","id":"poi1","location":{"lng":120.1,"lat":30.2}},{"name":"无坐标"}]}
            """);
        var found = client.search("住所", "全国");
        assertEquals(1, found.size()); assertEquals(120.1, found.getFirst().longitude()); assertEquals(30.2, found.getFirst().latitude());
        assertEquals("GCJ02", found.getFirst().coordinateSystem());
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertEquals("apis.map.qq.com", request.getValue().uri().getHost());
        assertEquals("/ws/place/v1/suggestion", request.getValue().uri().getPath());
        assertFalse(request.getValue().uri().getQuery().contains("region="));
        assertTrue(request.getValue().uri().getQuery().contains("key=synthetic-server-key"));
    }

    @Test void testGeocode_超长地址不发送到上游() throws Exception {
        var error = assertThrows(ResponseStatusException.class, () -> client.geocode("址".repeat(129)));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(http);
    }

    @Test void testFailure_HTTP成功不代表业务成功或存在有效坐标() {
        for (String body : new String[]{"{\"status\":302}", "{\"data\":[]}", "not json", "{\"status\":0,\"result\":{}}"}) {
            when(response.body()).thenReturn(body);
            assertThrows(ResponseStatusException.class, () -> client.geocode("示例地址"));
        }
        when(config.serviceKey()).thenReturn("");
        when(config.searchEnabled()).thenReturn(false);
        assertFalse(client.available());
        assertThrows(ResponseStatusException.class, () -> client.search("住所", "全国"));
    }

    @Test void testSearch_配置修改后下一次请求使用新Key() throws Exception {
        when(response.body()).thenReturn("{\"status\":0,\"data\":[]}");
        client.search("住所", "全国");
        when(config.serviceKey()).thenReturn("synthetic-replacement-key");
        client.search("住所", "全国");
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertTrue(requests.getAllValues().getLast().uri().getQuery().contains("key=synthetic-replacement-key"));
    }

    @Test void testReverse_上游描述不能移动手工标点() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"result":{"address":"示例地址","location":{"lng":121,"lat":31},"address_component":{"city":"杭州"},"ad_info":{"adcode":"330106"},"address_reference":{"town":{"id":"poi-town-id","title":"翠苑街道"}}}}
            """);
        var result = client.reverse(120.1, 30.2);
        assertEquals(120.1, result.longitude()); assertEquals(30.2, result.latitude());
        assertEquals("330106", result.region().districtCode());
        assertNull(result.region().townCode(), "腾讯 town.id 是地点标识，不当作行政区划编码");
        assertEquals("翠苑街道", result.region().townName());

    }

    @Test void testTimeout_下一次地图调用立即使用新的超时() throws Exception {
        when(response.body()).thenReturn("{\"status\":0,\"data\":[]}");
        when(config.timeoutMs()).thenReturn(1200, 8000);
        client.search("住所", "全国");
        client.search("住所", "全国");
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertEquals(1200, requests.getAllValues().get(0).timeout().orElseThrow().toMillis());
        assertEquals(8000, requests.getAllValues().get(1).timeout().orElseThrow().toMillis());
    }

    @Test void testCity_城市限定与特殊字符编码() throws Exception {
        when(response.body()).thenReturn("{\"status\":0,\"data\":[]}");
        client.search("A&B 住所", "杭州");
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertTrue(request.getValue().uri().getRawQuery().contains("keyword=A%26B+"));
        assertTrue(request.getValue().uri().getQuery().contains("region_fix=1"));
        assertTrue(request.getValue().uri().getQuery().contains("region=杭州"));
        verify(config, never()).displayKey();
    }

    @Test void testGeocode_腾讯精度与城市映射且中文地址不再按百度字节限制() throws Exception {
        when(response.body()).thenReturn("""
            {"status":0,"result":{"location":{"lng":120.1,"lat":30.2},"address_components":{"city":"杭州市"},"reliability":8,"level":9}}
            """);
        var result = client.geocode("址".repeat(43));
        assertTrue(result.precise()); assertEquals("门址", result.level()); assertEquals("杭州市", result.city());
        when(response.body()).thenReturn("""
            {"status":0,"result":{"location":{"lng":120.1,"lat":30.2},"reliability":6,"level":7}}
            """);
        assertFalse(client.geocode("示例道路").precise());
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertEquals("/ws/geocoder/v1/", requests.getValue().uri().getPath());
        assertFalse(requests.getValue().uri().getQuery().contains("location="));
    }

}
