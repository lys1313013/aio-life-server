package top.aiolife.security;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.aop.LogAspect;
import top.aiolife.record.service.impl.MailServiceImpl;
import top.aiolife.sso.mapper.MailLogMapper;
import top.aiolife.sso.pojo.entity.MailLogEntity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CredentialLoggingTest {
    private static final String PASSWORD = "test-only-password-marker";
    private static final String CODE = "test-only-code-628194";
    private static final String BODY = "验证码：" + CODE + ", password=" + PASSWORD;

    @ParameterizedTest
    @ValueSource(strings = {"/auth/register", "/user/changePassword", "/auth/resetPassword",
            "/user/secondary-password", "/auth/wechat/login", "/future/credentials/{id}"})
    void 请求日志只保留路由和耗时且正常返回不受影响(String route) throws Throwable {
        var point = mock(ProceedingJoinPoint.class);
        var response = Map.of("nested", Map.of("password", PASSWORD, "code", CODE));
        when(point.proceed()).thenReturn(response);
        var request = new MockHttpServletRequest("POST", "/" + PASSWORD);
        request.setQueryString("code=" + CODE);
        request.addHeader("Authorization", PASSWORD);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (var capture = new Capture(LogAspect.class)) {
            assertSame(response, new LogAspect().doAround(point));
            verify(point, never()).getArgs();
            assertTrue(capture.text().contains(route));
            assertTrue(capture.text().contains("completed=true durationMs="));
            assertSafe(capture.text());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void 异常请求仍记录失败耗时但不记录凭证或原始路径() throws Throwable {
        var point = mock(ProceedingJoinPoint.class);
        var failure = new IllegalArgumentException(BODY);
        when(point.proceed()).thenThrow(failure);
        var request = new MockHttpServletRequest("POST", "/" + PASSWORD);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (var capture = new Capture(LogAspect.class)) {
            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> new LogAspect().doAround(point)));
            assertTrue(capture.text().contains("[unmapped]"));
            assertTrue(capture.text().contains("completed=false"));
            assertSafe(capture.text());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void 异常日志保留类型和代码栈但不记录消息链及校验失败值() throws Exception {
        var failure = new IllegalArgumentException(PASSWORD, new RuntimeException(CODE));
        failure.addSuppressed(new RuntimeException(BODY));
        var binding = new BeanPropertyBindingResult(Map.of("password", PASSWORD), "request");
        binding.addError(new FieldError("request", "password", PASSWORD, false, null, null, BODY));
        var parameter = new MethodParameter(CredentialLoggingTest.class.getDeclaredMethod("request", String.class), 0);
        try (var capture = new Capture(ExceptionHandle.class)) {
            var handler = new ExceptionHandle();
            handler.handleException(failure);
            handler.handleDataAccessException(new DataIntegrityViolationException(BODY, failure));
            handler.handleMethodArgumentNotValidException(new MethodArgumentNotValidException(parameter, binding));
            assertTrue(capture.text().contains(IllegalArgumentException.class.getName()));
            assertTrue(capture.text().contains("CredentialLoggingTest.java:"));
            assertTrue(capture.text().contains("错误数量=1"));
            assertSafe(capture.text());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "reset_pwd", "secondary_pwd", "other"})
    void 验证码邮件正常发送但正文不进入日志和日志表(String bizType) {
        var sender = mock(JavaMailSenderImpl.class);
        var mapper = mock(MailLogMapper.class);
        var service = mailService(sender, mapper);
        try (var capture = new Capture(MailServiceImpl.class)) {
            service.sendSimpleEmail("user@example.test", "验证码", BODY, bizType, "127.0.0.1");
            var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender).send(mail.capture());
            assertEquals(BODY, mail.getValue().getText());
            var audit = captureAudit(mapper);
            assertEquals(1, audit.getStatus());
            assertEquals(bizType, audit.getBizType());
            assertEquals("[REDACTED]", audit.getContent());
            assertNull(audit.getErrorMsg());
            assertTrue(capture.text().contains("邮件发送成功"));
            assertSafe(capture.text());
        }
    }

    @Test
    void 发送失败及审计落库失败的异常消息均不会泄露邮件正文() {
        var sender = mock(JavaMailSenderImpl.class);
        var mapper = mock(MailLogMapper.class);
        doThrow(new MailSendException(BODY)).when(sender).send(any(SimpleMailMessage.class));
        doThrow(new DataIntegrityViolationException(BODY)).when(mapper).insert(any(MailLogEntity.class));
        try (var capture = new Capture(MailServiceImpl.class)) {
            assertThrows(MailSendException.class, () -> mailService(sender, mapper)
                    .sendSimpleEmail("user@example.test", "验证码", BODY, "register", "127.0.0.1"));
            var audit = captureAudit(mapper);
            assertEquals(0, audit.getStatus());
            assertEquals(MailSendException.class.getName(), audit.getErrorMsg());
            assertEquals("[REDACTED]", audit.getContent());
            assertTrue(capture.text().contains("保存邮件发送日志失败"));
            assertSafe(capture.text());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void HTML邮件成功与失败都不保存正文(boolean fail) throws Exception {
        var sender = mock(JavaMailSenderImpl.class);
        var mapper = mock(MailLogMapper.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        if (fail) doThrow(new MailSendException(BODY)).when(sender).send(any(MimeMessage.class));
        try (var capture = new Capture(MailServiceImpl.class)) {
            var service = mailService(sender, mapper);
            if (fail) {
                assertThrows(RuntimeException.class, () -> service.sendHtmlEmail("user@example.test", "验证码", BODY));
            } else {
                service.sendHtmlEmail("user@example.test", "验证码", BODY);
            }
            verify(sender).send(message);
            // 原文确实存在于发出的 MIME 邮件中，脱敏只作用于日志。
            message.saveChanges();
            var bytes = new ByteArrayOutputStream();
            message.writeTo(bytes);
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains(PASSWORD));
            var audit = captureAudit(mapper);
            assertEquals(fail ? 0 : 1, audit.getStatus());
            assertEquals("[REDACTED]", audit.getContent());
            if (fail) assertEquals(MailSendException.class.getName(), audit.getErrorMsg());
            assertSafe(capture.text());
        }
    }

    private void request(String password) {}

    private static MailServiceImpl mailService(JavaMailSenderImpl sender, MailLogMapper mapper) {
        var service = new MailServiceImpl();
        ReflectionTestUtils.setField(service, "mailSender", sender);
        ReflectionTestUtils.setField(service, "mailLogMapper", mapper);
        ReflectionTestUtils.setField(service, "sendFrom", "sender@example.test");
        return service;
    }

    private static MailLogEntity captureAudit(MailLogMapper mapper) {
        var captor = ArgumentCaptor.forClass(MailLogEntity.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    private static void assertSafe(String text) {
        assertFalse(text.contains(PASSWORD), "密码不得进入日志");
        assertFalse(text.contains(CODE), "验证码不得进入日志");
    }

    /** 捕获真实格式化日志（包含 Throwable），避免仅断言 mock 而漏掉异常消息。 */
    private static final class Capture extends AbstractAppender implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final StringBuilder output = new StringBuilder();

        Capture(Class<?> type) {
            super("credential-test", null, PatternLayout.newBuilder().withPattern("%m%n%ex").build(), false, null);
            logger = (Logger) LogManager.getLogger(type);
            previousLevel = logger.getLevel();
            start();
            logger.addAppender(this);
            logger.setLevel(Level.ALL);
        }

        @Override public void append(LogEvent event) {
            output.append(getLayout().toSerializable(event));
        }

        String text() { return output.toString(); }

        @Override public void close() {
            logger.removeAppender(this);
            logger.setLevel(previousLevel);
            stop();
        }
    }
}
