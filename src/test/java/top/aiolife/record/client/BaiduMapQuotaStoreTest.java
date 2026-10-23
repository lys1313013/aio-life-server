package top.aiolife.record.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaiduMapQuotaStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-10-22T15:59:00Z");
    private final BaiduMapQuotaStore store = new BaiduMapQuotaStore(redis, clock);

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(start);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(any())).thenReturn(Arrays.asList(null, null));
    }

    @ParameterizedTest @ValueSource(ints = {4, 302})
    void testDaily_缓存到北京时间次日零点且不保存AK和地址(int status) {
        var ticket = store.beforeQuery("synthetic-secret", "/geocoding/v3/");
        assertFalse(ticket.dailyKey().contains("synthetic-secret"));
        assertTrue(ticket.dailyKey().endsWith(":day:2026-10-22"));
        store.recordExhausted(ticket, status);
        verify(values).setIfAbsent(ticket.dailyKey(), Integer.toString(status), Duration.ofSeconds(60));
    }

    @Test void testPermanent_永久配额24小时后重新探测() {
        var ticket = store.beforeQuery("synthetic-secret", "/geocoding/v3/");
        store.recordExhausted(ticket, 301);
        verify(values).setIfAbsent(ticket.permanentKey(), "301", Duration.ofHours(24));
    }

    @Test void testBlocked_任一额度状态均跳过查询() {
        when(values.multiGet(any())).thenReturn(Arrays.asList("302", null), Arrays.asList(null, "301"));
        assertThrows(MapQuotaExceededException.class, () -> store.beforeQuery("key", "/geocoding/v3/"));
        assertThrows(MapQuotaExceededException.class, () -> store.beforeQuery("key", "/geocoding/v3/"));
        verify(values, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test void testScope_跨实例一致且更换AK与接口不会互相污染() {
        var ticket = store.beforeQuery("key-a", "/geocoding/v3/");
        var otherInstance = new BaiduMapQuotaStore(redis, Clock.fixed(start, ZoneOffset.UTC));
        assertEquals(ticket, otherInstance.beforeQuery("key-a", "/geocoding/v3/"));
        assertNotEquals(ticket.dailyKey(), store.beforeQuery("key-b", "/geocoding/v3/").dailyKey());
        assertNotEquals(ticket.dailyKey(), store.beforeQuery("key-a", "/reverse_geocoding/v3/").dailyKey());
    }

    @Test void testMidnight_迟到错误不污染次日额度() {
        var ticket = store.beforeQuery("key", "/geocoding/v3/");
        when(clock.instant()).thenReturn(start.plusSeconds(61));
        store.recordExhausted(ticket, 302);
        var nextDay = store.beforeQuery("key", "/geocoding/v3/");
        assertNotEquals(ticket.dailyKey(), nextDay.dailyKey());
        verify(values, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test void testReadFailure_状态不可读时允许备用且脱敏() {
        when(values.multiGet(any())).thenThrow(new RedisConnectionFailureException("secret-connection"));
        var failure = assertThrows(MapFallbackException.class, () -> store.beforeQuery("key", "/geocoding/v3/"));
        assertFalse(failure.toString().contains("secret-connection"));
        assertNull(failure.getCause());
    }

    @Test void testWriteFailure_状态写入失败仍允许备用() {
        var ticket = store.beforeQuery("key", "/geocoding/v3/");
        when(values.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("secret-connection"));
        assertThrows(MapFallbackException.class, () -> store.recordExhausted(ticket, 302));
    }
}
