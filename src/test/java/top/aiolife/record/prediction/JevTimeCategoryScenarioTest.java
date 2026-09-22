package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.service.impl.TimeRecordServiceImpl;
import top.aiolife.system.service.IWorkCalendarService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevTimeCategoryScenarioTest {
    static Stream<JsonNode> scenarios() throws IOException {
        return JevScenarioSupport.scenarios().stream();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-21,2026-09-20", "2026-09-22,2026-09-21",
            "2026-09-23,2026-09-22", "2026-09-24,2026-09-23",
            "2026-09-25,2026-09-19", "2026-09-26,2026-09-25",
            "2026-09-27,2026-09-26", "2026-01-03,2026-01-02",
            "2026-09-20,2026-09-18", "2026-10-08,2026-09-30",
            "2026-10-10,2026-10-09", "2026-10-11,2026-10-07"
    })
    void testReferenceDate_生产推荐使用日历服务返回的参考日期(String target, String expected) {
        var mapper = mock(ITimeRecordMapper.class);
        var calendar = spy(JevCalendarFixture.calendar());
        var categories = mock(top.aiolife.record.service.ITimeTrackerCategoryService.class);
        var category = new top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity();
        category.setId(104L);
        category.setName("工作");
        when(categories.listUserVisibleCategories(1L)).thenReturn(java.util.List.of(category));
        var service = new TimeRecordServiceImpl(mapper, null, null, null, calendar, org.mockito.Mockito.mock(top.aiolife.record.prediction.JevCategoryRecommendationService.class, invocation -> null), new top.aiolife.record.prediction.RecommendationDataCache(15000), categories);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var referenceRecord = new TimeRecordEntity();
        referenceRecord.setCategoryId(104L);
        when(mapper.findReferenceRecords(1L, expected, 600, 1440)).thenReturn(java.util.List.of(referenceRecord));

        try (var clock = mockStatic(java.time.LocalDateTime.class, CALLS_REAL_METHODS)) {
            var now = java.time.LocalDateTime.of(2026, 12, 31, 12, 0);
            clock.when(java.time.LocalDateTime::now).thenReturn(now);
            assertEquals(104L, service.recommendType(1L, target, 600, null));
        }
        verify(mapper).findReferenceRecords(1L, expected, 600, 1440);
        verifyNoMoreInteractions(mapper);
        verify(calendar).findPreviousComparableDate(LocalDate.parse(target));
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void testRequest_过滤无关日期并导出可直接调用的请求(JsonNode scenario) throws IOException {
        ObjectNode request = JevScenarioSupport.request(scenario);
        JsonNode state = request.path("state");
        assertEquals(scenario.path("expectedReferenceDate").asText(), state.path("previousComparableDay").path("date").asText());
        assertEquals(scenario.path("expectedTodayCount").asInt(), state.path("todayRecords").size());
        assertEquals(scenario.path("expectedReferenceCount").asInt(), state.path("previousComparableDay").path("records").size());
        assertFalse(state.has("previousCategoryId"));
        if (scenario.path("expectedPreviousCategoryId").isNull()) {
            assertTrue(state.path("previousCategoryName").isNull());
        } else {
            assertEquals(scenario.path("categories").path(scenario.path("expectedPreviousCategoryId").asText()),
                    state.path("previousCategoryName"));
        }
        assertEquals(scenario.path("expectedIsWorkday").asBoolean(), state.path("target").path("isWorkday").asBoolean());
        assertFalse(state.path("target").has("dayOfWeek"));
        assertFalse(state.path("target").has("isWeekday"));
        assertFalse(state.path("target").has("minute"));
        LocalTime targetTime = LocalTime.parse(state.path("target").path("time").asText());
        assertEquals(scenario.path("target").path("minute").asInt(), targetTime.toSecondOfDay() / 60);
        var criteria = request.path("questions").path("current_category").path("criteria");
        assertFalse(criteria.has("unknown"));
        assertEquals(scenario.path("categories"), criteria);
        for (JsonNode record : state.path("todayRecords")) {
            assertEquals(scenario.path("target").path("date"), record.path("date"));
            assertTrue(LocalTime.parse(record.path("endTime").asText()).isBefore(targetTime));
            assertModelRecord(scenario, record);
            assertFalse(record.has("startMinute"));
            assertFalse(record.has("endMinute"));
        }
        for (JsonNode record : state.path("previousComparableDay").path("records")) {
            assertEquals(scenario.path("expectedReferenceDate"), record.path("date"));
            assertModelRecord(scenario, record);
            assertFalse(record.has("startMinute"));
            assertFalse(record.has("endMinute"));
        }
        Path file = Path.of("target/jev-calendar-scenarios", scenario.path("id").asText() + ".request.json");
        Files.createDirectories(file.getParent());
        JevScenarioSupport.JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), request);
    }

    @ParameterizedTest
    @CsvSource({"0,00:00", "59,00:59", "60,01:00", "811,13:31", "1286,21:26", "1310,21:50", "1439,23:59"})
    void testRequest_目标和记录均转换为钟表时间且保留原始分钟(int minute, String expected) {
        var scenario = JevScenarioSupport.JSON.createObjectNode();
        scenario.putObject("target").put("date", "2026-09-22").put("minute", minute);
        scenario.putObject("categories").put("10", "理财");
        scenario.putArray("records").addObject().put("date", "2026-09-21")
                .put("startMinute", minute).put("endMinute", minute).put("categoryId", "10");
        var original = scenario.deepCopy();
        var state = JevCategoryProtocol.request(scenario, true, LocalDate.of(2026, 9, 21)).path("state");
        assertEquals(expected, state.path("target").path("time").asText());
        var record = state.path("previousComparableDay").path("records").get(0);
        assertEquals(expected, record.path("startTime").asText());
        assertEquals(expected, record.path("endTime").asText(), "保留包含末分钟的语义，不加一分钟");
        assertFalse(record.has("categoryId"));
        assertEquals("理财", record.path("categoryName").asText());
        assertTrue(state.path("previousCategoryName").isNull());
        assertEquals(original, scenario, "模型格式转换不能修改业务分钟数据");
    }

    private void assertModelRecord(JsonNode scenario, JsonNode record) {
        assertFalse(record.has("categoryId"));
        assertEquals(4, record.size(), "模型记录仅包含日期、起止时间及中文分类");
        int minute = LocalTime.parse(record.path("startTime").asText()).toSecondOfDay() / 60;
        var original = java.util.stream.StreamSupport.stream(scenario.path("records").spliterator(), false)
                .filter(item -> item.path("date").equals(record.path("date"))
                        && item.path("startMinute").asInt() == minute)
                .findFirst().orElseThrow();
        assertEquals(scenario.path("categories").path(original.path("categoryId").asText()), record.path("categoryName"));
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void testMockHttp_验证认证请求和分类映射而非模型效果(JsonNode scenario) throws IOException {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        ObjectNode request = JevScenarioSupport.request(scenario);
        ObjectNode response = JevScenarioSupport.mockResponse(scenario);
        server.expect(requestTo(JevScenarioSupport.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer mock-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(request.toString()))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        JsonNode actual = JevScenarioSupport.evaluate(builder.build(), "mock-key", request);
        var decision = JevScenarioSupport.decision(actual, scenario.path("categories"));
        assertEquals("PREDICTED", decision.reason());
        assertEquals(scenario.path("expectedCategoryId").asText(), decision.categoryId());
        server.verify();
        var report = JevScenarioSupport.JSON.createObjectNode();
        report.put("mode", "MOCK_NOT_REAL_JEV");
        report.set("request", request);
        report.set("response", actual);
        report.put("acceptedCategoryId", decision.categoryId());
        report.put("decision", decision.reason());
        Path file = Path.of("target/jev-calendar-scenarios", scenario.path("id").asText() + ".mock-result.json");
        Files.createDirectories(file.getParent());
        JevScenarioSupport.JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "", "999999"})
    void testResponse_拒绝未知空值和集合外分类(String choice) throws IOException {
        JsonNode scenario = JevScenarioSupport.scenarios().getFirst();
        ObjectNode response = JevScenarioSupport.mockResponse(scenario);
        ((ObjectNode) response.path("answers").path("current_category")).put("choice", choice);
        assertEquals(new JevScenarioSupport.Decision(null, "INVALID_RESPONSE"),
                JevScenarioSupport.decision(response, scenario.path("categories")));
    }

    @Test
    void testRequest_采用日历服务结果并忽略输入星期字段() throws IOException {
        ObjectNode scenario = JevScenarioSupport.scenarios().getFirst().deepCopy();
        ((ObjectNode) scenario.path("target")).put("isWeekday", true).put("dayOfWeek", 2);
        var calendar = mock(IWorkCalendarService.class);
        LocalDate date = LocalDate.parse(scenario.path("target").path("date").asText());
        when(calendar.isWorkday(date)).thenReturn(false);
        when(calendar.findPreviousComparableDate(date)).thenReturn(LocalDate.of(2026, 9, 19));
        JsonNode state = JevScenarioSupport.request(scenario, calendar).path("state");
        assertFalse(state.path("target").path("isWorkday").asBoolean());
        assertFalse(state.path("target").has("dayOfWeek"));
        assertFalse(state.path("target").has("isWeekday"));
        assertEquals("2026-09-19", state.path("previousComparableDay").path("date").asText());
        verify(calendar).isWorkday(date);
        verify(calendar).findPreviousComparableDate(date);
    }

    @Test
    void testRequest_目标日历缺失时不组装外部请求() throws IOException {
        ObjectNode scenario = JevScenarioSupport.scenarios().getFirst().deepCopy();
        ((ObjectNode) scenario.path("target")).put("date", "2027-01-04");
        var error = assertThrows(IllegalStateException.class, () -> JevScenarioSupport.request(scenario));
        assertEquals("WORK_CALENDAR_MISSING", error.getMessage());
    }

    @Test
    void testRequest_参考日未知保留空日期不擅自用昨天() throws IOException {
        ObjectNode scenario = JevScenarioSupport.scenarios().getFirst().deepCopy();
        ((ObjectNode) scenario.path("target")).put("date", "2026-01-01");
        JsonNode state = JevScenarioSupport.request(scenario).path("state");
        assertFalse(state.path("target").path("isWorkday").asBoolean());
        assertTrue(state.path("previousComparableDay").path("date").isNull());
        assertTrue(state.path("previousComparableDay").path("isWorkday").isNull());
        assertEquals(0, state.path("previousComparableDay").path("records").size());
    }

    @Test
    void testResponse_低置信度标记为规则降级() throws IOException {
        JsonNode scenario = JevScenarioSupport.scenarios().getFirst();
        ObjectNode response = JevScenarioSupport.mockResponse(scenario);
        ((ObjectNode) response.path("answers").path("current_category")).put("confidence", 0.4);
        assertEquals(new JevScenarioSupport.Decision(null, "LOW_CONFIDENCE"),
                JevScenarioSupport.decision(response, scenario.path("categories")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingAnswer", "missingConfidence", "wrongSum", "foreignProbability", "wrongType", "numericChoice"})
    void testResponse_拒绝不完整或非法概率响应(String mutation) throws IOException {
        JsonNode scenario = JevScenarioSupport.scenarios().getFirst();
        ObjectNode response = JevScenarioSupport.mockResponse(scenario);
        var answer = (ObjectNode) response.path("answers").path("current_category");
        switch (mutation) {
            case "missingAnswer" -> response.remove("answers");
            case "missingConfidence" -> answer.remove("confidence");
            case "wrongSum" -> ((ObjectNode) answer.path("probabilities")).put("104", 0.2);
            case "foreignProbability" -> ((ObjectNode) answer.path("probabilities")).put("999999", 0);
            case "wrongType" -> answer.put("type", "score");
            case "numericChoice" -> answer.put("choice", 104);
            default -> throw new IllegalArgumentException(mutation);
        }
        assertEquals("INVALID_RESPONSE", JevScenarioSupport.decision(response, scenario.path("categories")).reason());
    }

    @Test
    void testHttp_认证失败不会被识别为分类结果() throws IOException {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(JevScenarioSupport.ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(HttpClientErrorException.Unauthorized.class, () -> JevScenarioSupport.evaluate(
                builder.build(), "mock-key", JevScenarioSupport.request(JevScenarioSupport.scenarios().getFirst())));
        server.verify();
    }
}
