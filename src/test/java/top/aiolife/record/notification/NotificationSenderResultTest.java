package top.aiolife.record.notification;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.service.IMailService;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.IMessageService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSenderResultTest {
    @Test
    void email_成功返回真失败返回假() throws Exception {
        var mail = mock(IMailService.class);
        var sender = new EmailNotificationSender();
        ReflectionTestUtils.setField(sender, "mailService", mail);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@example.com");
        assertTrue(sender.send(user, "title", "html", "text"));
        doThrow(new IllegalStateException("failure")).when(mail).sendHtmlEmail(any(), any(), any(), any(), any());
        assertFalse(sender.send(user, "title", "html", "text"));
    }

    @Test
    void email_没有邮箱不能误记成功() {
        var sender = new EmailNotificationSender();
        var user = new UserEntity();
        user.setId(7L);
        assertFalse(sender.send(user, "title", "html", "text"));
    }

    @Test
    void station_成功返回真失败返回假() {
        var messages = mock(IMessageService.class);
        var sender = new SystemNotificationSender();
        ReflectionTestUtils.setField(sender, "messageService", messages);
        var user = new UserEntity();
        user.setId(7L);
        assertTrue(sender.send(user, "title", "html", "text"));
        when(messages.createMessage(any())).thenThrow(new IllegalStateException("failure"));
        assertFalse(sender.send(user, "title", "html", "text"));
    }
}
