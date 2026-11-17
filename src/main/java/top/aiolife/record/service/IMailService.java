package top.aiolife.record.service;

import jakarta.mail.MessagingException;

/**
 * 邮件服务接口
 *
 * @author Lys
 * @date 2025/04/12 00:50
 */
public interface IMailService {

    /**
     * 发送简单邮件
     *
     * @param sendTo  收件人
     * @param title   标题
     * @param content 内容
     */
    void sendSimpleEmail(String sendTo, String title, String content);

    /**
     * 发送简单邮件（带业务类型和客户端IP）
     *
     * @param sendTo   收件人
     * @param title    标题
     * @param content  内容
     * @param bizType  业务类型
     * @param clientIp 客户端IP
     */
    void sendSimpleEmail(String sendTo, String title, String content, String bizType, String clientIp);

    /**
     * 发送HTML邮件
     *
     * @param sendTo      收件人
     * @param title       标题
     * @param htmlContent html内容
     */
    void sendHtmlEmail(String sendTo, String title, String htmlContent) throws MessagingException;

    /**
     * 发送HTML邮件（带业务类型和客户端IP）
     *
     * @param sendTo      收件人
     * @param title       标题
     * @param htmlContent html内容
     * @param bizType     业务类型
     * @param clientIp    客户端IP
     */
    void sendHtmlEmail(String sendTo, String title, String htmlContent, String bizType, String clientIp) throws MessagingException;
}
