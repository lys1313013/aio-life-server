package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.api.FootprintMapController;
import top.aiolife.record.filter.TencentMapProxySessionFilter;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService;
import top.aiolife.system.service.FootprintTencentConfigService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FootprintMapClientTest {
    private final FootprintMapProvider baidu = mock(FootprintMapProvider.class);
    private final FootprintMapProvider tencent = mock(FootprintMapProvider.class);
    private final FootprintMapConfigService config = mock(FootprintMapConfigService.class);
    private final FootprintMapClient client = new FootprintMapClient(baidu, tencent, config);
    private final FootprintLocationVO location = new FootprintLocationVO("住所", "示例地址", "杭州",
            120.1, 30.2, "GCJ02", "synthetic-poi", true, "POI");

    @BeforeEach void setup() {
        when(baidu.available()).thenReturn(true);
        when(tencent.available()).thenReturn(true);
    }

    @Test void testPrimary_三类查询均优先百度且空结果不触发备用() {
        when(baidu.search("住所", "全国")).thenReturn(List.of());
        when(baidu.geocode("示例地址")).thenReturn(location);
        when(baidu.reverse(120.1, 30.2)).thenReturn(location);
        assertEquals(List.of(), client.search(1, "住所", "全国"));
        assertSame(location, client.geocode(1, "示例地址"));
        assertSame(location, client.reverse(1, 120.1, 30.2));
        verifyNoInteractions(tencent);
    }

    @Test void testFallback_三类查询在额度耗尽后原样传参且仅尝试一次腾讯() {
        when(baidu.search("A&B", "杭州")).thenThrow(new MapQuotaExceededException());
        when(baidu.geocode("杭州市示例地址")).thenThrow(new MapQuotaExceededException());
        when(baidu.reverse(120.1, 30.2)).thenThrow(new MapQuotaExceededException());
        when(tencent.search("A&B", "杭州")).thenReturn(List.of(location));
        when(tencent.geocode("杭州市示例地址")).thenReturn(location);
        when(tencent.reverse(120.1, 30.2)).thenReturn(location);
        assertEquals(List.of(location), client.search(1, "A&B", "杭州"));
        assertSame(location, client.geocode(1, "杭州市示例地址"));
        assertSame(location, client.reverse(1, 120.1, 30.2));
        var order = inOrder(baidu, tencent);
        order.verify(baidu).search("A&B", "杭州"); order.verify(tencent).search("A&B", "杭州");
        verify(tencent).geocode("杭州市示例地址"); verify(tencent).reverse(120.1, 30.2);
    }

    @Test void testConfiguration_仅腾讯可用与两家均未配置() {
        when(baidu.available()).thenReturn(false);
        when(tencent.search("住所", "全国")).thenReturn(List.of(location));
        assertTrue(client.available());
        assertEquals(List.of(location), client.search(1, "住所", "全国"));
        verify(baidu, never()).search(anyString(), anyString());
        when(tencent.available()).thenReturn(false);
        assertFalse(client.available());
        assertThrows(ResponseStatusException.class, () -> client.geocode(1, "示例地址"));
        verify(tencent, never()).geocode(anyString());
    }

    @Test void testUnavailableBackup_百度耗尽且未配置腾讯给出明确错误() {
        when(baidu.geocode(anyString())).thenThrow(new MapQuotaExceededException());
        when(tencent.available()).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class, () -> client.geocode(1, "示例地址"));
        assertTrue(error.getReason().contains("尚未配置腾讯"));
        verify(tencent, never()).geocode(anyString());
    }

    @Test void testFailure_普通失败不切换且腾讯失败不循环重试() {
        var failure = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "模拟上游错误");
        when(baidu.geocode(anyString())).thenThrow(failure);
        assertSame(failure, assertThrows(ResponseStatusException.class, () -> client.geocode(1, "地址")));
        verifyNoInteractions(tencent);
        doThrow(new MapQuotaExceededException()).when(baidu).geocode(anyString());
        when(tencent.geocode(anyString())).thenThrow(new MapQuotaExceededException());
        assertThrows(MapQuotaExceededException.class, () -> client.geocode(1, "地址"));
        verify(baidu, times(2)).geocode("地址"); verify(tencent).geocode("地址");
    }

    @Test void testConcurrencyRecovery_短暂并发限制后下一请求重新尝试百度() {
        when(baidu.geocode(anyString())).thenThrow(new MapFallbackException("地图查询并发受限")).thenReturn(location);
        when(tencent.geocode(anyString())).thenReturn(location);
        assertSame(location, client.geocode(1, "地址"));
        assertSame(location, client.geocode(1, "地址"));
        verify(baidu, times(2)).geocode("地址"); verify(tencent).geocode("地址");
    }

    @Test void testRateLimit_切换只计算一次请求且异常释放并发许可() {
        doThrow(new MapQuotaExceededException()).when(baidu).geocode(anyString());
        when(tencent.geocode(anyString())).thenThrow(new MapQuotaExceededException());
        for (int i = 0; i < 60; i++) assertThrows(MapQuotaExceededException.class, () -> client.geocode(1, "地址"));
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> client.geocode(1, "地址")).getStatusCode().value());
        verify(baidu, times(60)).geocode("地址"); verify(tencent, times(60)).geocode("地址");
        assertThrows(MapQuotaExceededException.class, () -> client.geocode(2, "地址"));
    }

    @Test void testConfig_任一服务配置即可搜索且不返回凭据() throws Exception {
        when(tencent.available()).thenReturn(false);
        when(config.tileSource()).thenReturn(new FootprintMapConfigService.TileSource(
                FootprintMapConfigService.DEFAULT_TILE_URL, "OSM", "", "WGS84"));
        var display = mock(FootprintTencentConfigService.class);
        when(display.browserConfig()).thenReturn(new FootprintTencentConfigService.BrowserConfig(true, "style1"));
        var value = new FootprintMapController(client, display, mock(TencentMapProxySessionFilter.class))
                .config(new MockHttpServletRequest(), new MockHttpServletResponse()).getData();
        assertTrue(value.searchEnabled());
        var json = new ObjectMapper().writeValueAsString(value);
        assertFalse(json.contains("Key")); assertFalse(json.contains("Ciphertext"));
        assertEquals("GCJ02", value.coordinateSystem());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 301, 302, 401, 402})
    void testHttpFallback_实际适配器识别百度配额及并发码并映射腾讯结果(int code) throws Exception {
        var harness = harness("{\"status\":" + code + "}");
        assertEquals("腾讯住所", harness.client.search(1, "住所", "全国").getFirst().placeName());
        verify(harness.tencentHttp).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 210, 211, 240})
    void testHttpFailure_鉴权参数等错误不得触发备用(int code) throws Exception {
        var harness = harness("{\"status\":" + code + "}");
        assertThrows(ResponseStatusException.class, () -> harness.client.search(1, "住所", "全国"));
        verifyNoInteractions(harness.tencentHttp);
    }

    @Test void testTransport_超时HTTP错误及畸形响应不切换且脱敏() throws Exception {
        var harness = harness("not json");
        assertThrows(ResponseStatusException.class, () -> harness.client.search(1, "住所", "全国"));
        when(harness.baiduResponse.statusCode()).thenReturn(500);
        when(harness.baiduResponse.body()).thenReturn("{\"status\":302}");
        assertThrows(ResponseStatusException.class, () -> harness.client.search(1, "住所", "全国"));
        when(harness.baiduHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("https://api.map.baidu.com/?ak=synthetic-baidu-key"));
        var failure = assertThrows(ResponseStatusException.class, () -> harness.client.search(1, "住所", "全国"));
        assertFalse(failure.toString().contains("synthetic-baidu-key")); assertNull(failure.getCause());
        verifyNoInteractions(harness.tencentHttp);
    }

    @Test void testHttpRateLimit_百度HTTP429转腾讯且不依赖响应体() throws Exception {
        var harness = harness("not json");
        when(harness.baiduResponse.statusCode()).thenReturn(429);
        assertEquals("腾讯住所", harness.client.search(1, "住所", "全国").getFirst().placeName());
        verify(harness.tencentHttp).send(any(), any());
    }

    private record Harness(FootprintMapClient client, HttpClient baiduHttp,
                           HttpClient tencentHttp, HttpResponse<String> baiduResponse) {}

    @SuppressWarnings("unchecked")
    private Harness harness(String baiduBody) throws Exception {
        var baiduConfig = mock(FootprintMapConfigService.class);
        var tencentConfig = mock(FootprintTencentConfigService.class);
        when(baiduConfig.searchEnabled()).thenReturn(true); when(baiduConfig.serviceKey()).thenReturn("synthetic-baidu-key");
        when(tencentConfig.searchEnabled()).thenReturn(true); when(tencentConfig.serviceKey()).thenReturn("synthetic-tencent-key");
        when(baiduConfig.timeoutMs()).thenReturn(1000); when(tencentConfig.timeoutMs()).thenReturn(1000);
        HttpClient b = mock(HttpClient.class), t = mock(HttpClient.class);
        HttpResponse<String> br = mock(HttpResponse.class), tr = mock(HttpResponse.class);
        when(b.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(br);
        when(t.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(tr);
        when(br.statusCode()).thenReturn(200); when(br.body()).thenReturn(baiduBody);
        when(tr.statusCode()).thenReturn(200); when(tr.body()).thenReturn("""
            {"status":0,"data":[{"title":"腾讯住所","address":"示例地址","city":"杭州","id":"synthetic-poi","location":{"lng":120.1,"lat":30.2}}]}
            """);
        var client = new FootprintMapClient(new BaiduFootprintMapProvider(baiduConfig, new ObjectMapper(), b, mock(BaiduMapQuotaStore.class)),
                new TencentFootprintMapProvider(tencentConfig, new ObjectMapper(), t), baiduConfig);
        return new Harness(client, b, t, br);
    }
}
