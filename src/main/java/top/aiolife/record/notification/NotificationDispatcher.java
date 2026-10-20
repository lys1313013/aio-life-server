package top.aiolife.record.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.record.service.FeishuNotificationService;

/** 业务只提交事件，各外部渠道独立入队。 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {
    private final FeishuNotificationService feishu;
    private final WechatSubscriptionService wechat;

    public void sendIfEnabled(NotificationRequest request) {
        feishu.sendIfEnabled(request);
        wechat.sendIfEnabled(request);
    }

    public boolean isChannelEnabled(long userId, String bizType, String channel) {
        return feishu.isChannelEnabled(userId, bizType, channel);
    }
}
