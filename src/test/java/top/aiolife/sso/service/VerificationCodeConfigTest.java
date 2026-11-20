package top.aiolife.sso.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.aiolife.sso.config.AuthAbuseProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationCodeConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AuthAbuseProperties.class);

    @Test
    void testBinding_配置文件及覆盖值正确注入实际限流字段() {
        contextRunner.run(context -> {
            var code = context.getBean(AuthAbuseProperties.class).getCode();
            assertEquals(180, code.getIntervalSeconds());
            assertEquals(100, code.getIpLimitDay());
            assertEquals(20, code.getEmailLimitDay());
            assertEquals(1000, code.getTotalLimitDay());
        });
        contextRunner.withPropertyValues(
                "aio.life.server.auth.code.interval-seconds=42",
                "aio.life.server.auth.code.ip-limit-day=43",
                "aio.life.server.auth.code.email-limit-day=44",
                "aio.life.server.auth.code.total-limit-day=45"
        ).run(context -> {
            var code = context.getBean(AuthAbuseProperties.class).getCode();
            assertEquals(42, code.getIntervalSeconds());
            assertEquals(43, code.getIpLimitDay());
            assertEquals(44, code.getEmailLimitDay());
            assertEquals(45, code.getTotalLimitDay());
        });
    }
}
