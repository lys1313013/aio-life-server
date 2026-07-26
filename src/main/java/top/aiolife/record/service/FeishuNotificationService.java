package top.aiolife.record.service;

import top.aiolife.record.notification.NotificationRequest;
import top.aiolife.record.pojo.req.FeishuChannelSaveReq;
import top.aiolife.record.pojo.req.NotificationPreferenceUpdateReq;
import top.aiolife.record.pojo.vo.FeishuChannelConfigVO;
import top.aiolife.record.pojo.vo.FeishuRecipientListVO;
import top.aiolife.record.pojo.vo.NotificationPreferenceVO;

import java.util.List;

public interface FeishuNotificationService {
    FeishuChannelConfigVO getConfig(long userId);

    FeishuChannelConfigVO saveConfig(long userId, FeishuChannelSaveReq req);

    FeishuRecipientListVO listRecipients(long userId);

    void deleteConfig(long userId);

    void sendTest(long userId);

    List<NotificationPreferenceVO> listPreferences(long userId);

    List<NotificationPreferenceVO> updatePreferences(long userId, NotificationPreferenceUpdateReq req);

    void sendIfEnabled(NotificationRequest request);

    boolean isChannelEnabled(long userId, String bizType, String channel);

    void retryPending();
}
