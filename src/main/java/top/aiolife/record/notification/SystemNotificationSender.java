package top.aiolife.record.notification;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.IMessageService;

/**
 * 系统通知发送者（站内信）
 *
 * @author Lys
 * @date 2025/04/10
 */
@Slf4j
@Component
public class SystemNotificationSender extends AbstractNotificationSender {

    @Resource
    private IMessageService messageService;

    @Override
    public String getChannel() {
        return NotificationChannel.STATION;
    }

    @Override
    public void send(UserEntity user, String title, String htmlContent, String textContent) {
        try {
            MessageEntity message = new MessageEntity();
            // 系统管理账号ID为1
            message.setSenderId(1L);
            message.setReceiverId(user.getId());
            message.setTitle(title);
            // 站内信使用纯文本内容
            message.setContent(textContent);
            // 0-系统通知
            message.setType(0);
            // 设置创建人（系统管理员）
            message.setCreateUser(1L);
            // 设置更新人（系统管理员）
            message.setUpdateUser(1L);

            messageService.createMessage(message);
            log.info("系统通知发送成功，用户ID：{}", user.getId());
        } catch (Exception e) {
            log.error("系统通知发送失败，用户ID：" + user.getId(), e);
        }
    }
}
