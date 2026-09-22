package top.aiolife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigTest {

    @Test
    void jackson自动配置_保留大整数ID和日期响应契约() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JsonConfig.class)
                .withPropertyValues(
                        "spring.jackson.date-format=yyyy-MM-dd HH:mm:ss",
                        "spring.jackson.serialization.write-dates-as-timestamps=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    var payload = new Payload(9007199254740993L, 9007199254740995L,
                            LocalDate.of(2026, 9, 22), LocalDateTime.of(2026, 9, 22, 10, 30));
                    var json = mapper.readTree(mapper.writeValueAsString(payload));

                    assertTrue(json.get("id").isTextual());
                    assertEquals("9007199254740993", json.get("id").textValue());
                    assertTrue(json.get("parentId").isTextual());
                    assertEquals("9007199254740995", json.get("parentId").textValue());
                    assertEquals("2026-09-22", json.get("date").textValue());
                    assertEquals("2026-09-22T10:30:00", json.get("createTime").textValue());
                    assertEquals(payload, mapper.treeToValue(json, Payload.class));
                });
    }

    private record Payload(Long id, long parentId, LocalDate date, LocalDateTime createTime) {
    }
}
