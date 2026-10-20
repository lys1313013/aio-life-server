package top.aiolife.record.notification;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.mapper.NotificationDeliveryMapper;
import top.aiolife.record.mapper.NotificationPreferenceMapper;
import top.aiolife.record.pojo.entity.NotificationDeliveryEntity;
import top.aiolife.record.pojo.entity.NotificationPreferenceEntity;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.wechat.WechatMiniClient;
import top.aiolife.sso.wechat.WechatMiniProperties;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatSubscriptionServiceTest {
    private final WechatSubscriptionProperties properties = new WechatSubscriptionProperties();
    private final WechatMiniProperties mini = new WechatMiniProperties();
    private final WechatMiniClient client = mock(WechatMiniClient.class);
    private final UserMapper users = mock(UserMapper.class);
    private final NotificationPreferenceMapper preferences = mock(NotificationPreferenceMapper.class);
    private final NotificationDeliveryMapper deliveries = mock(NotificationDeliveryMapper.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final NotificationCryptoService crypto = new NotificationCryptoService("test-encryption-key-only");
    private final WechatSubscriptionService service = new WechatSubscriptionService(properties, mini, client, users,
            preferences, deliveries, crypto, new ObjectMapper(), locks, mock(DataSourceTransactionManager.class));
    private final UserEntity user = new UserEntity();
    private final NotificationPreferenceEntity preference = new NotificationPreferenceEntity();
    private NotificationDeliveryEntity row;
    private final NotificationRequest request = new NotificationRequest(7L, "LEETCODE_REMINDER", "提醒😀".repeat(10),
            "今天尚未打卡", null, "leetcode:reminder:fixture:7");

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), NotificationDeliveryEntity.class);
        properties.setEnabled(true);
        properties.setTemplateId("fixture-template");
        mini.setAppId("wx_fixture");
        user.setId(7L);
        user.setIsDeleted(0);
        user.setWechatOpenid("fixture-openid");
        preference.setEnabled(1);
        when(client.enabled()).thenReturn(true);
        when(users.selectById(7L)).thenReturn(user);
        when(preferences.selectOne(any())).thenReturn(preference);
        when(locks.tryRun(anyString(), any())).thenAnswer(inv -> { inv.<Runnable>getArgument(1).run(); return true; });
        when(deliveries.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<NotificationDeliveryEntity> page = inv.getArgument(0);
            return page.setRecords(row == null ? List.of() : List.of(row));
        });
        when(deliveries.insert(any(NotificationDeliveryEntity.class))).thenAnswer(inv -> {
            row = inv.getArgument(0);
            row.setId(9L);
            return 1;
        });
        when(deliveries.selectById(9L)).thenAnswer(inv -> row);
        when(deliveries.update(isNull(), any())).thenReturn(1);
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("0", true, false));
    }

    private void enqueueAndProcess() {
        service.sendIfEnabled(request);
        assertNotNull(row);
        service.deliverPending();
    }

    @Test void 默认关闭且绑定身份不会返回前端() {
        properties.setEnabled(false);
        var config = service.getConfig(7L);
        assertFalse(config.enabled());
        assertTrue(config.bound());
        assertNull(config.templateId());
        service.sendIfEnabled(request);
        verify(deliveries, never()).insert(any(NotificationDeliveryEntity.class));
    }

    @Test void 无业务偏好无绑定或非试点事件均不入队() {
        when(preferences.selectOne(any())).thenReturn(null);
        service.sendIfEnabled(request);
        user.setWechatOpenid(null);
        service.sendIfEnabled(request);
        service.sendIfEnabled(new NotificationRequest(7L, "LEETCODE_DAILY", "每日题目", "正文", null, "daily"));
        verify(deliveries, never()).insert(any(NotificationDeliveryEntity.class));
    }

    @Test void 载荷加密且正确模板字段按Unicode字符截断() throws Exception {
        enqueueAndProcess();
        assertTrue(row.getPayloadCiphertext().startsWith("v1:"));
        assertFalse(row.getPayloadCiphertext().contains("fixture-openid"));
        var payload = new ObjectMapper().readValue(crypto.decrypt(row.getPayloadCiphertext()), WechatSubscriptionService.Payload.class);
        String title = payload.data().get("thing1").get("value");
        assertEquals(20, title.codePointCount(0, title.length()));
        assertFalse(Character.isHighSurrogate(title.charAt(title.length() - 1)));
        verify(client).sendSubscription(eq("fixture-openid"), eq("fixture-template"), eq("formal"), any());
        assertEquals("SUCCESS", row.getStatus());
    }

    @Test void 重复业务事件不再次入队且已成功记录不重发() {
        enqueueAndProcess();
        when(deliveries.selectCount(any())).thenReturn(1L);
        service.sendIfEnabled(request);
        service.deliverPending();
        verify(deliveries, times(1)).insert(any(NotificationDeliveryEntity.class));
        verify(client, times(1)).sendSubscription(any(), any(), any(), any());
    }

    @Test void 无订阅机会和结果未知均不自动重试() {
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("43101", false, false));
        enqueueAndProcess();
        assertEquals("FAILED", row.getStatus());
        service.deliverPending();
        verify(client, times(1)).sendSubscription(any(), any(), any(), any());
    }

    @Test void 超时结果未知不会因下一轮扫描再次消费额度() {
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("UNKNOWN", false, false));
        enqueueAndProcess();
        assertEquals("UNKNOWN", row.getStatus());
        service.deliverPending();
        verify(client, times(1)).sendSubscription(any(), any(), any(), any());
    }

    @Test void 明确限流按退避重试且到期前不重复请求() {
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("45009", false, true));
        enqueueAndProcess();
        assertEquals("RETRY", row.getStatus());
        assertEquals(1, row.getRetryCount());
        service.deliverPending();
        verify(client, times(1)).sendSubscription(any(), any(), any(), any());
        row.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("0", true, false));
        service.deliverPending();
        assertEquals("SUCCESS", row.getStatus());
        assertNull(row.getNextRetryTime());
    }

    @Test void 入队后更换绑定或关闭偏好不得向旧接收人发送() {
        service.sendIfEnabled(request);
        user.setWechatOpenid("different-openid");
        service.deliverPending();
        assertEquals("FAILED", row.getStatus());
        verify(client, never()).sendSubscription(any(), any(), any(), any());
    }

    @Test void 入队后关闭偏好或跨日的提醒不再发送() {
        service.sendIfEnabled(request);
        preference.setEnabled(0);
        service.deliverPending();
        assertEquals("FAILED", row.getStatus());
        row.setStatus("PENDING");
        preference.setEnabled(1);
        row.setCreateTime(LocalDateTime.now().minusDays(1));
        service.deliverPending();
        assertEquals("EXPIRED", row.getProviderCode());
        verify(client, never()).sendSubscription(any(), any(), any(), any());
    }

    @Test void 测试发送使用当前用户且无额度时给出可恢复错误() {
        when(client.sendSubscription(any(), any(), any(), any()))
                .thenReturn(new WechatMiniClient.SubscriptionResult("43101", false, false));
        var error = assertThrows(IllegalStateException.class, () -> service.sendTest(7L));
        assertTrue(error.getMessage().contains("订阅一次"));
        assertEquals(7L, row.getUserId());
        assertEquals("FAILED", row.getStatus());
    }

    @Test void 发送前占位失败不请求微信且发送后保存失败不重新发送() {
        when(deliveries.update(isNull(), any())).thenReturn(0);
        enqueueAndProcess();
        verify(client, never()).sendSubscription(any(), any(), any(), any());
        when(deliveries.update(isNull(), any())).thenReturn(1, 0);
        service.deliverPending();
        assertEquals("SENDING", row.getStatus());
        service.deliverPending();
        verify(client, times(1)).sendSubscription(any(), any(), any(), any());
    }
}
