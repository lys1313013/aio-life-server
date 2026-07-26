package top.aiolife.record.notification;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.service.IMailService;
import top.aiolife.sso.pojo.entity.UserEntity;

/**
 * 邮件通知发送者
 *
 * @author Lys
 * @date 2025/04/10
 */
@Slf4j
@Component
public class EmailNotificationSender extends AbstractNotificationSender {

    @Resource
    private IMailService mailService;

    @Override
    public String getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(UserEntity user, String title, String htmlContent, String textContent) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            log.warn("用户 {} 未设置邮箱，跳过邮件发送", user.getId());
            return;
        }
        try {
            // 使用HTML内容发送邮件
            mailService.sendHtmlEmail(user.getEmail(), title, htmlContent, "leetcode_daily_question", "system");
            log.info("邮件通知发送成功，用户ID：{}", user.getId());
        } catch (Exception e) {
            log.error("邮件通知发送失败，用户ID：" + user.getId(), e);
        }
    }
}
