package top.aiolife.record.service.impl;

import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import top.aiolife.record.service.IMailService;
import top.aiolife.sso.mapper.MailLogMapper;
import top.aiolife.sso.pojo.entity.MailLogEntity;
import top.aiolife.core.util.LogSafeException;

import java.time.LocalDateTime;

/**
 * 邮件服务实现类
 *
 * @author Lys
 * @date 2025/04/12 00:50
 */
@Slf4j
@Service
public class MailServiceImpl implements IMailService {

    /**
     * 发送邮件的邮箱账号
     */
    @Value("${spring.mail.username}")
    private String sendFrom;

    @Resource
    private JavaMailSenderImpl mailSender;

    @Resource
    private MailLogMapper mailLogMapper;

    @Override
    public void sendSimpleEmail(String sendTo, String title, String content) {
        this.sendSimpleEmail(sendTo, title, content, "other", null);
    }

    @Override
    public void sendSimpleEmail(String sendTo, String title, String content, String bizType, String ipAddress) {
        MailLogEntity logEntity = new MailLogEntity();
        logEntity.setSendTo(sendTo);
        logEntity.setSubject(title);
        // 邮件日志仅作投递审计；正文可能携带验证码，不复制到持久化日志。
        logEntity.setContent("[REDACTED]");
        logEntity.setBizType(bizType);
        logEntity.setIpAddress(ipAddress);
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setStatus(0); // 默认失败

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sendFrom);
            message.setTo(sendTo);
            message.setSubject(title);
            message.setText(content);
            mailSender.send(message);

            logEntity.setStatus(1);
            log.info("邮件发送成功");
        } catch (Exception e) {
            logEntity.setStatus(0);
            logEntity.setErrorMsg(e.getClass().getName());
            log.error("邮件发送失败", LogSafeException.withoutMessages(e));
            throw e;
        } finally {
            try {
                mailLogMapper.insert(logEntity);
            } catch (Exception e) {
                log.error("保存邮件发送日志失败", LogSafeException.withoutMessages(e));
            }
        }
    }

    @Override
    public void sendHtmlEmail(String sendTo, String title, String htmlContent) throws MessagingException {
        this.sendHtmlEmail(sendTo, title, htmlContent, "other", null);
    }

    @Override
    public void sendHtmlEmail(String sendTo, String title, String htmlContent, String bizType, String ipAddress) throws MessagingException {
        MailLogEntity logEntity = new MailLogEntity();
        logEntity.setSendTo(sendTo);
        logEntity.setSubject(title);
        logEntity.setContent("[REDACTED]");
        logEntity.setBizType(bizType);
        logEntity.setIpAddress(ipAddress);
        logEntity.setCreateTime(LocalDateTime.now());
        logEntity.setStatus(0); // 默认失败

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(sendFrom);
            helper.setTo(sendTo);
            helper.setSubject(title);
            helper.setText(htmlContent, true);
            mailSender.send(message);

            logEntity.setStatus(1);
            log.info("HTML邮件发送成功");
        } catch (MessagingException e) {
            logEntity.setStatus(0);
            logEntity.setErrorMsg(e.getClass().getName());
            log.error("HTML邮件发送失败", LogSafeException.withoutMessages(e));
            throw e;
        } catch (Exception e) {
            logEntity.setStatus(0);
            logEntity.setErrorMsg(e.getClass().getName());
            log.error("HTML邮件发送失败", LogSafeException.withoutMessages(e));
            throw new RuntimeException(e);
        } finally {
            try {
                mailLogMapper.insert(logEntity);
            } catch (Exception e) {
                log.error("保存邮件发送日志失败", LogSafeException.withoutMessages(e));
            }
        }
    }
}
