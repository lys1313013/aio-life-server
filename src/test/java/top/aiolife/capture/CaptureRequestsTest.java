package top.aiolife.capture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import top.aiolife.capture.service.CaptureRequests;

import java.util.concurrent.atomic.AtomicReference;

class CaptureRequestsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final AtomicReference<String> pending = new AtomicReference<>();
    private CaptureRequests requests;

    @BeforeEach
    void setup() {
        requests = new CaptureRequests(redis, json);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(CaptureRequests.RETENTION)))
                .thenAnswer(
                        inv -> {
                            if (pending.get() != null) return false;
                            pending.set(inv.getArgument(1));
                            return true;
                        });
        when(values.get(anyString())).thenAnswer(inv -> pending.get());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void resetTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void 已提交但缓存失败保留pending且禁止自动重新执行() {
        var payload = json.createObjectNode().put("idempotencyKey", "same-key");
        var attempt = requests.begin(1, "direct", payload);
        var result = json.createObjectNode().put("operation", "created");
        requests.finish(attempt, result);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("simulated"));
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        assertEquals("unconfirmed", result.path("retryStatus").asText());
        assertFalse(result.has("replayExpiresAt"));
        assertTrue(result.path("warnings").size() > 0);
        assertNotNull(pending.get());
        assertThrows(RuntimeException.class, () -> requests.begin(1, "direct", payload));
    }

    @Test
    void 只有明确回滚才清理pending未知事务状态保留() {
        requests.begin(1, "direct", json.createObjectNode().put("idempotencyKey", "rollback-key"));
        var sync = TransactionSynchronizationManager.getSynchronizations().getFirst();
        sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any());
        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(redis).execute(any(DefaultRedisScript.class), anyList(), any());
    }

    @Test
    void Redis预留不可用时拒绝开始业务写入() {
        when(values.setIfAbsent(anyString(), anyString(), eq(CaptureRequests.RETENTION)))
                .thenThrow(new RedisConnectionFailureException("simulated"));
        assertThrows(
                RuntimeException.class,
                () ->
                        requests.begin(
                                1, "direct", json.createObjectNode().put("idempotencyKey", "key")));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }
}
