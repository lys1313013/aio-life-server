package top.aiolife.record.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import top.aiolife.record.client.GoalQueryClient;
import top.aiolife.record.pojo.entity.GoalEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoalProgressEvaluatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final GoalQueryClient client = mock(GoalQueryClient.class);
    private final GoalProgressEvaluator evaluator = new GoalProgressEvaluator(json, client);
    private GoalEntity goal() {
        var goal = new GoalEntity(); goal.setProgressMode("graphql"); goal.setProgressQuery("{progress}");
        goal.setProgressVariables("{}"); goal.setProgressVariableBindings("{}");
        goal.setProgressValuePath("progress.value"); goal.setTargetValue(new BigDecimal("100"));
        goal.setStartDate(LocalDateTime.parse("2024-02-15T10:30:00")); goal.setEndDate(LocalDateTime.parse("2024-02-29T23:59:59"));
        return goal;
    }
    @Test void 日期绑定覆盖闰月结束日且保留大整数ID字符串() {
        var goal = goal(); goal.setProgressVariables("{\"type\":\"9007199254740993123\"}");
        goal.setProgressVariableBindings("{\"start\":\"goal.startDate\",\"end\":\"goal.endDateExclusive\",\"monthStart\":\"goal.startMonth\",\"monthEnd\":\"goal.endMonthExclusive\"}");
        var vars = evaluator.variables(goal);
        assertEquals("2024-02-15", vars.path("start").asText()); assertEquals("2024-03-01", vars.path("end").asText());
        assertEquals("2024-02-01", vars.path("monthStart").asText()); assertEquals("2024-03-01", vars.path("monthEnd").asText());
        assertEquals("9007199254740993123", vars.path("type").asText());
    }
    @Test void 绑定不允许覆盖固定变量或注入用户身份上下文() {
        var goal = goal(); goal.setProgressVariables("{\"start\":\"2026-01-01\"}"); goal.setProgressVariableBindings("{\"start\":\"goal.startDate\"}");
        assertThrows(IllegalArgumentException.class, () -> evaluator.validate(goal));
        goal.setProgressVariableBindings("{\"user\":\"user.id\"}"); assertThrows(IllegalArgumentException.class, () -> evaluator.validate(goal));
        goal.setProgressVariables("[]"); assertThrows(IllegalArgumentException.class, () -> evaluator.validate(goal));
    }
    @Test void 变量必须是单个完整JSON对象() {
        var goal = goal();
        for (String source : new String[]{"{} {}", "{} invalid", "true", "[]"}) {
            goal.setProgressVariables(source);
            assertThrows(IllegalArgumentException.class, () -> evaluator.validate(goal));
        }
    }
    @Test void 字符串数字小数空集合和数组路径均可提取() throws Exception {
        var goal = goal(); goal.setTargetValue(new BigDecimal("15000.25"));
        when(client.execute(anyString(), any(), eq("fixture"))).thenReturn(json.readTree("{\"progress\":{\"value\":\"15000.25\"}}"));
        assertTrue(evaluator.evaluate(goal, "fixture").isAchieved());
        goal.setProgressValuePath("rows.0.amount");
        when(client.execute(anyString(), any(), eq("fixture"))).thenReturn(json.readTree("{\"rows\":[{\"amount\":31}]}"));
        assertEquals(0, evaluator.evaluate(goal, "fixture").getCurrentValue().compareTo(new BigDecimal("31")));
        goal.setProgressValuePath("progress.value");
        when(client.execute(anyString(), any(), eq("fixture"))).thenReturn(json.readTree("{\"progress\":{\"value\":null}}"));
        assertEquals(0, evaluator.evaluate(goal, "fixture").getCurrentValue().signum());
    }
    @Test void 缺失路径对象布尔非数字负数与超精度均拒绝() throws Exception {
        for (String value : new String[]{"{}", "true", "\"NaN\"", "\"Infinity\"", "-1", "1.001", "1000000000000"}) {
            when(client.execute(anyString(), any(), anyString())).thenReturn(json.readTree("{\"progress\":{\"value\":" + value + "}}"));
            assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(goal(), "fixture"), value);
        }
        when(client.execute(anyString(), any(), anyString())).thenReturn(json.readTree("{\"progress\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(goal(), "fixture"));
    }
    @Test void 严格超过在相等时最多99百分比且巨大比值不会溢出() throws Exception {
        var goal = goal(); goal.setTargetOperator("gt");
        when(client.execute(anyString(), any(), anyString())).thenReturn(json.readTree("{\"progress\":{\"value\":100}}"));
        assertFalse(evaluator.evaluate(goal, "fixture").isAchieved()); assertEquals(99, evaluator.evaluate(goal, "fixture").getProgressPercent());
        when(client.execute(anyString(), any(), anyString())).thenReturn(json.readTree("{\"progress\":{\"value\":101}}"));
        assertTrue(evaluator.evaluate(goal, "fixture").isAchieved());
        goal.setTargetValue(new BigDecimal("0.01"));
        when(client.execute(anyString(), any(), anyString())).thenReturn(json.readTree("{\"progress\":{\"value\":\"999999999999.99\"}}"));
        assertEquals(100, evaluator.evaluate(goal, "fixture").getProgressPercent());
    }
}
