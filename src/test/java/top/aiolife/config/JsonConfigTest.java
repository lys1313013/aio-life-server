package top.aiolife.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigTest {

    @Test
    void 统一响应使用整数业务码和新字段且不改变长ID() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JsonConfig.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    var success = mapper.readTree(mapper.writeValueAsString(
                            ApiResponse.success(Map.of("id", 9007199254740993L))));
                    assertEquals(Set.of("code", "message", "data"), mapper.convertValue(success, Map.class).keySet());
                    assertTrue(success.path("code").isIntegralNumber());
                    assertEquals(0, success.path("code").intValue());
                    assertTrue(success.path("message").isNull());
                    assertEquals("9007199254740993", success.at("/data/id").textValue());

                    var empty = mapper.readTree(mapper.writeValueAsString(ApiResponse.success()));
                    assertTrue(empty.path("data").isNull());

                    var failure = mapper.readTree(mapper.writeValueAsString(ApiResponse.error("工具不存在")));
                    assertTrue(failure.path("code").isIntegralNumber());
                    assertEquals(ResponseCodeConst.COMMON_FAIL, failure.path("code").intValue());
                    assertEquals("工具不存在", failure.path("message").textValue());
                    assertTrue(failure.path("data").isNull());

                    var lock = mapper.readTree(mapper.writeValueAsString(ApiResponse.error(
                            ResponseCodeConst.SECONDARY_LOCK_REQUIRED, "需要二级密码验证", Map.of("menuPath", "/finance"))));
                    assertEquals(2001, lock.path("code").intValue());
                    assertEquals("/finance", lock.at("/data/menuPath").textValue());
                });
    }

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
