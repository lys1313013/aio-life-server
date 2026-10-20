package top.aiolife.record.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.mapper.NotificationChannelConfigMapper;
import top.aiolife.record.mapper.NotificationDeliveryMapper;
import top.aiolife.record.mapper.NotificationPreferenceMapper;
import top.aiolife.record.pojo.req.NotificationPreferenceUpdateReq;
import top.aiolife.record.pojo.entity.NotificationPreferenceEntity;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.service.impl.FeishuNotificationServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatSubscriptionPreferenceTest {
    private final NotificationPreferenceMapper preferences = mock(NotificationPreferenceMapper.class);
    private final FeishuNotificationServiceImpl service = new FeishuNotificationServiceImpl(
            mock(NotificationChannelConfigMapper.class), preferences, mock(NotificationDeliveryMapper.class),
            mock(NotificationCryptoService.class), mock(FeishuAppClient.class), new ObjectMapper(),
            mock(IUserBindService.class), mock(DistributedLockExecutor.class), mock(DataSourceTransactionManager.class));

    @Test void 新用户微信默认关闭且只出现在试点业务中() {
        when(preferences.selectList(any())).thenReturn(List.of());
        var all = service.listPreferences(7L);
        var reminder = all.stream().filter(p -> p.getBizType().equals("LEETCODE_REMINDER")).findFirst().orElseThrow();
        assertFalse(reminder.getChannels().stream().filter(c -> c.getChannel().equals("WECHAT_MINI")).findFirst().orElseThrow().isEnabled());
        assertTrue(reminder.getChannels().stream().filter(c -> c.getChannel().equals("EMAIL")).findFirst().orElseThrow().isEnabled());
        assertTrue(all.stream().filter(p -> !p.getBizType().equals("LEETCODE_REMINDER"))
                .noneMatch(p -> p.getChannels().stream().anyMatch(c -> c.getChannel().equals("WECHAT_MINI"))));
        assertFalse(service.isChannelEnabled(7L, "LEETCODE_REMINDER", "WECHAT_MINI"));
    }

    @Test void 不允许为未实现的业务打开微信提醒() {
        var item = new NotificationPreferenceUpdateReq.Item();
        item.setBizType("FEEDBACK_REPLY");
        item.setChannel("WECHAT_MINI");
        item.setEnabled(true);
        var request = new NotificationPreferenceUpdateReq();
        request.setItems(List.of(item));
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(7L, request));
        verify(preferences, never()).insert(any(NotificationPreferenceEntity.class));
    }
}
