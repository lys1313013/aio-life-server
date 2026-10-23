package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.pojo.vo.FootprintLocationVO;
import top.aiolife.system.service.FootprintMapConfigService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅连接显式提供的临时本机 Redis；所有 AK 都是随机测试值，不访问地图厂商。 */
@EnabledIfSystemProperty(named = "aio.test.redis.port", matches = "[0-9]+")
class BaiduMapQuotaRedisTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T15:59:00Z"), ZoneOffset.UTC);
    private static final FootprintLocationVO LOCATION = new FootprintLocationVO("腾讯结果", "测试地址", "杭州",
            120.1, 30.2, "GCJ02", null, true, "POI");

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("aio.test.redis.port")));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
    }
    @AfterAll static void close() { connection.destroy(); }

    @ParameterizedTest
    @CsvSource({"4,search", "301,search", "302,search", "4,geocode", "301,geocode", "302,geocode",
            "4,reverse", "301,reverse", "302,reverse"})
    void testFallback_三类查询缓存耗尽跨实例跳过百度(int status, String operation) throws Exception {
        var h = harness(status);
        query(h.client(), operation);
        // 模拟另一个服务实例，共享真实 Redis，不共享 Provider 或额度存储对象。
        query(h.client(), operation);
        verify(h.http, times(1)).send(any(), any());
        switch (operation) {
            case "search" -> verify(h.tencent, times(2)).search("测试", "全国");
            case "geocode" -> verify(h.tencent, times(2)).geocode("测试地址");
            default -> verify(h.tencent, times(2)).reverse(120.1, 30.2);
        }
        var path = switch (operation) {
            case "search" -> "/place/v2/search";
            case "geocode" -> "/geocoding/v3/";
            default -> "/reverse_geocoding/v3/";
        };
        var ticket = h.ticket(path);
        String marker = status == 301 ? ticket.permanentKey() : ticket.dailyKey();
        assertEquals(Integer.toString(status), redis.opsForValue().get(marker));
        long ttl = redis.getExpire(marker, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= (status == 301 ? 86400 : 60));
    }

    @ParameterizedTest @CsvSource({"401,200", "402,200", "0,429"})
    void testConcurrency_短暂限流不缓存且下一次成功回百度(int status, int httpStatus) throws Exception {
        var h = harness(status);
        when(h.response.statusCode()).thenReturn(httpStatus);
        assertEquals("腾讯结果", h.client().search(1, "测试", "全国").getFirst().placeName());
        when(h.response.statusCode()).thenReturn(200);
        when(h.response.body()).thenReturn("{\"status\":0,\"results\":[]}");
        assertTrue(h.client().search(1, "测试", "全国").isEmpty());
        verify(h.http, times(2)).send(any(), any());
        verify(h.tencent, times(1)).search(anyString(), anyString());
        var ticket = h.ticket("/place/v2/search");
        assertFalse(redis.hasKey(ticket.dailyKey())); assertFalse(redis.hasKey(ticket.permanentKey()));
    }

    @Test void testExpiry_额度状态过期后重新尝试百度() throws Exception {
        var h = harness(302);
        h.client().search(1, "测试", "全国");
        var marker = h.ticket("/place/v2/search").dailyKey();
        redis.expire(marker, Duration.ofMillis(1));
        Thread.sleep(20);
        when(h.response.body()).thenReturn("{\"status\":0,\"results\":[]}");
        assertTrue(h.client().search(1, "测试", "全国").isEmpty());
        verify(h.http, times(2)).send(any(), any());
        verify(h.tencent, times(1)).search(anyString(), anyString());
    }

    @Test void testRotation_在途旧AK耗尽不得污染新AK或其他API() throws Exception {
        var h = harness(302);
        var oldTicket = h.ticket("/place/v2/search");
        when(h.http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            when(h.config.serviceKey()).thenReturn("replacement-" + UUID.randomUUID());
            return h.response;
        });
        h.client().search(1, "测试", "全国");
        assertTrue(redis.hasKey(oldTicket.dailyKey()));
        when(h.response.body()).thenReturn("{\"status\":0,\"results\":[]}");
        assertTrue(h.client().search(1, "测试", "全国").isEmpty());
        // 原 AK 的另一 API 没有被搜索配额停用。
        new BaiduMapQuotaStore(redis, CLOCK).beforeQuery(h.key, "/geocoding/v3/");
        verify(h.http, times(2)).send(any(), any());
    }

    @Test void testBackupFailure_腾讯失败仍记录百度额度且下次跳过百度() throws Exception {
        var h = harness(302);
        when(h.tencent.search(anyString(), anyString())).thenThrow(new MapQuotaExceededException());
        assertThrows(MapQuotaExceededException.class, () -> h.client().search(1, "测试", "全国"));
        assertThrows(MapQuotaExceededException.class, () -> h.client().search(1, "测试", "全国"));
        verify(h.http, times(1)).send(any(), any());
    }

    @Test void testNoBackup_未配置腾讯也记录百度额度() throws Exception {
        var h = harness(302);
        when(h.tencent.available()).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> h.client().search(1, "测试", "全国"));
        assertThrows(ResponseStatusException.class, () -> h.client().search(1, "测试", "全国"));
        verify(h.http, times(1)).send(any(), any());
        verify(h.tencent, never()).search(anyString(), anyString());
    }

    private void query(FootprintMapClient client, String operation) {
        var result = switch (operation) {
            case "search" -> client.search(1, "测试", "全国").getFirst();
            case "geocode" -> client.geocode(1, "测试地址");
            default -> client.reverse(1, 120.1, 30.2);
        };
        assertEquals(LOCATION, result);
        assertEquals("GCJ02", result.coordinateSystem());
    }

    private record Harness(String key, FootprintMapConfigService config, HttpClient http,
                           HttpResponse<String> response, FootprintMapProvider tencent) {
        FootprintMapClient client() {
            return new FootprintMapClient(new BaiduFootprintMapProvider(config, new ObjectMapper(), http,
                    new BaiduMapQuotaStore(redis, CLOCK)), tencent, config);
        }
        BaiduMapQuotaStore.Ticket ticket(String path) {
            // 独立的空存储只计算相同状态标识；真实读写与断言仍使用 Redis。
            var emptyRedis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
            when(emptyRedis.opsForValue().multiGet(any())).thenReturn(Arrays.asList(null, null));
            return new BaiduMapQuotaStore(emptyRedis, CLOCK).beforeQuery(key, path);
        }
    }

    @SuppressWarnings("unchecked")
    private Harness harness(int status) throws Exception {
        var config = mock(FootprintMapConfigService.class);
        String key = "synthetic-" + UUID.randomUUID();
        when(config.serviceKey()).thenReturn(key); when(config.searchEnabled()).thenReturn(true);
        when(config.timeoutMs()).thenReturn(1000);
        var http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn("{\"status\":" + status + "}");
        var tencent = mock(FootprintMapProvider.class);
        when(tencent.available()).thenReturn(true);
        when(tencent.search(anyString(), anyString())).thenReturn(List.of(LOCATION));
        when(tencent.geocode(anyString())).thenReturn(LOCATION);
        when(tencent.reverse(anyDouble(), anyDouble())).thenReturn(LOCATION);
        return new Harness(key, config, http, response, tencent);
    }
}
