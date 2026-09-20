package top.aiolife.record.notification;

import top.aiolife.sso.pojo.entity.UserEntity;

/**
 * 抽象通知发送者
 *
 * @author Lys
 * @date 2025/04/10
 */
public abstract class AbstractNotificationSender {

    /**
     * 发送通知
     *
     * @param user        接收用户
     * @param title       标题
     * @param htmlContent HTML格式内容
     * @param textContent 纯文本格式内容
     * @return 是否实际发送成功；跳过或失败返回 false
     */
    public abstract boolean send(UserEntity user, String title, String htmlContent, String textContent);

    public abstract String getChannel();
}
