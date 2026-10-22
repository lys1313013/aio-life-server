package top.aiolife.sso.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.sso.service.impl.UserServiceImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationCodeConfigTest {
    @Test void testBinding_配置文件及覆盖值正确注入实际限流字段() throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        beanFactory.addEmbeddedValueResolver(environment::resolveRequiredPlaceholders);
        var injector = new AutowiredAnnotationBeanPostProcessor();
        injector.setBeanFactory(beanFactory);
        var service = mock(UserServiceImpl.class);
        injector.processInjection(service);
        assertEquals(180L, ReflectionTestUtils.getField(service, "intervalSeconds"));
        assertEquals(100L, ReflectionTestUtils.getField(service, "ipLimitDay"));
        assertEquals(20L, ReflectionTestUtils.getField(service, "emailLimitDay"));
        assertEquals(1000L, ReflectionTestUtils.getField(service, "totalLimitDay"));
        environment.getPropertySources().addFirst(new MapPropertySource("overrides", Map.of(
                "aio.life.server.auth.code.interval-seconds", 42,
                "aio.life.server.auth.code.ip-limit-day", 43,
                "aio.life.server.auth.code.email-limit-day", 44,
                "aio.life.server.auth.code.total-limit-day", 45)));
        injector.processInjection(service);
        assertEquals(42L, ReflectionTestUtils.getField(service, "intervalSeconds"));
        assertEquals(43L, ReflectionTestUtils.getField(service, "ipLimitDay"));
        assertEquals(44L, ReflectionTestUtils.getField(service, "emailLimitDay"));
        assertEquals(45L, ReflectionTestUtils.getField(service, "totalLimitDay"));
    }
}
