package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import top.aiolife.export.config.ExportProperties;

class ExportPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                    .withUserConfiguration(ExportProperties.class);

    @Test
    void 未配置开关时默认开启数据导出() {
        assertTrue(new ExportProperties().isEnabled());
        contextRunner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBean(ExportProperties.class).isEnabled());
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "AIO_LIFE_DATA_EXPORT_ENABLED=false",
                "aio.life.server.data-export.enabled=false"
            })
    void 显式关闭配置覆盖默认值(String property) {
        contextRunner
                .withPropertyValues(property)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertFalse(context.getBean(ExportProperties.class).isEnabled());
                        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "AIO_LIFE_DATA_EXPORT_ENABLED=true",
                "aio.life.server.data-export.enabled=true"
            })
    void 支持显式开启配置(String property) {
        contextRunner
                .withPropertyValues(property)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTrue(context.getBean(ExportProperties.class).isEnabled());
                        });
    }
}
