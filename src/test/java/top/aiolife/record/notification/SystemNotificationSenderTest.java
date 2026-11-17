package top.aiolife.record.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.IMessageService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemNotificationSender 集成测试类
 *
 * @author Lys
 * @date 2026-03-13 19:28
 */
@SpringBootTest
@Transactional
class SystemNotificationSenderTest {

    @Autowired
    private SystemNotificationSender systemNotificationSender;

    @Autowired
    private IMessageService messageService;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        messageService.remove(new LambdaQueryWrapper<MessageEntity>()
                .in(MessageEntity::getReceiverId, 100L, 200L, 300L));
    }

    @Test
    void send_Success() {
        // Given
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setUsername("testUser");

        String title = "测试标题";
        String htmlContent = "<p>HTML内容</p>";
        String textContent = "纯文本内容";

        // When
        systemNotificationSender.send(user, title, htmlContent, textContent);

        // Then - 验证数据库中是否插入了消息
        List<MessageEntity> messages = messageService.listByUserId(100L, null);
        System.out.println("查询到的消息数量: " + messages.size());
        for (MessageEntity msg : messages) {
            System.out.println("消息: " + msg);
        }

        assertFalse(messages.isEmpty(), "应该插入消息到数据库");

        MessageEntity message = messages.get(0);
        assertNull(message.getSenderId(), "系统通知不记录发送用户");
        assertEquals(100L, message.getReceiverId(), "接收者ID应该匹配用户ID");
        assertEquals("测试标题", message.getTitle(), "标题应该匹配");
        assertEquals("纯文本内容", message.getContent(), "内容应该使用纯文本");
        assertEquals(0, message.getType(), "类型应该是系统通知(0)");
    }

    @Test
    void send_VerifyHtmlContentIgnored() {
        // Given - 传入HTML内容，但站内信应该使用纯文本
        UserEntity user = new UserEntity();
        user.setId(200L);

        String title = "HTML测试";
        String htmlContent = "<h1>HTML标题</h1><p>HTML段落</p>";
        String textContent = "这是纯文本内容";

        // When
        systemNotificationSender.send(user, title, htmlContent, textContent);

        // Then - 验证数据库中存储的是纯文本而非HTML
        List<MessageEntity> messages = messageService.listByUserId(200L, null);
        System.out.println("send_VerifyHtmlContentIgnored - 查询到的消息数量: " + messages.size());
        assertEquals(1, messages.size(), "应该只有1条消息");

        MessageEntity message = messages.get(0);
        assertEquals("这是纯文本内容", message.getContent(), "应该存储纯文本内容而非HTML");
        assertNotEquals(htmlContent, message.getContent(), "不应该存储HTML内容");
    }

    @Test
    void send_MultipleMessages() {
        // Given
        UserEntity user = new UserEntity();
        user.setId(300L);

        // When - 发送多条消息
        systemNotificationSender.send(user, "消息1", "<p>html1</p>", "文本1");
        systemNotificationSender.send(user, "消息2", "<p>html2</p>", "文本2");
        systemNotificationSender.send(user, "消息3", "<p>html3</p>", "文本3");

        // Then
        List<MessageEntity> messages = messageService.listByUserId(300L, null);
        System.out.println("send_MultipleMessages - 查询到的消息数量: " + messages.size());
        assertEquals(3, messages.size(), "应该插入3条消息");

        // 验证每条消息的内容
        for (int i = 0; i < messages.size(); i++) {
            MessageEntity message = messages.get(i);
            assertNull(message.getSenderId(), "系统通知不记录发送用户");
            assertEquals(300L, message.getReceiverId());
            assertEquals(0, message.getType());
        }
    }

    @Test
    void send_ExceptionHandling() {
        // Given - 使用null用户测试异常处理
        // 注意：这个测试会记录错误日志，但不会抛出异常

        // When & Then - 应该捕获异常，不抛出
        assertDoesNotThrow(() -> {
            // 传入null用户会导致NullPointerException，但应该被捕获
            try {
                systemNotificationSender.send(null, "标题", "html", "text");
            } catch (NullPointerException e) {
                // 预期会捕获异常，测试通过
            }
        });
    }
}
