package top.aiolife.record.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.pojo.entity.UserEntity;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSendGuardTest {
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final RedisUtil redis = mock(RedisUtil.class);
    private final AbstractNotificationSender sender = mock(AbstractNotificationSender.class);
    private final NotificationSendGuard guard = new NotificationSendGuard(locks, redis);
    private final UserEntity user = new UserEntity();
    private final NotificationRequest request = new NotificationRequest(7L, "LEETCODE_REMINDER", "title", "text", null, "reminder:2026-09-20:7");

    @BeforeEach
    void setup() {
        user.setId(7L);
        when(sender.getChannel()).thenReturn("EMAIL");
        when(locks.tryRun(anyString(), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        });
    }

    @Test
    void sendOnce_成功后保存独立标记() {
        when(sender.send(any(), any(), any(), any())).thenReturn(true);
        guard.sendOnce(sender, user, request, "html");
        verify(redis).set(contains("LEETCODE_REMINDER:reminder:2026-09-20:7:7:EMAIL"), eq("1"), eq(2L), eq(TimeUnit.DAYS));
    }

    @Test
    void sendOnce_重复事件不再次发送() {
        when(redis.hasKey(anyString())).thenReturn(true);
        guard.sendOnce(sender, user, request, "html");
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void sendOnce_失败或跳过不写成功标记() {
        guard.sendOnce(sender, user, request, "html");
        verify(redis, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void sendOnce_Redis不可用时不绕过锁发送() {
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> guard.sendOnce(sender, user, request, "html"));
        verify(sender, never()).send(any(), any(), any(), any());
    }
}
