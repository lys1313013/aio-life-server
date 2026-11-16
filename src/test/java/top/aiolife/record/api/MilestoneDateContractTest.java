package top.aiolife.record.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.aiolife.config.JsonConfig;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.req.MilestoneCreateReq;
import top.aiolife.record.pojo.req.MilestoneUpdateReq;

import static org.junit.jupiter.api.Assertions.*;

class MilestoneDateContractTest {
    private final ObjectMapper json = new JsonConfig()
            .jacksonObjectMapper(new Jackson2ObjectMapperBuilder());

    @Test
    void 日期按纯日期解析并以驼峰字段往返() throws Exception {
        var request = json.readValue("""
                {"title":"模拟里程碑","date":"2024-02-29","endDate":"2024-03-01"}
                """, MilestoneCreateReq.class);
        var entity = RecordApiConvertor.INSTANCE.fromMilestoneCreateReq(request);
        assertEquals(LocalDate.of(2024, 2, 29), entity.getDate());
        assertEquals(LocalDate.of(2024, 3, 1), entity.getEndDate());
        var result = json.readTree(json.writeValueAsString(RecordApiConvertor.INSTANCE.toMilestoneVO(entity)));
        assertEquals("2024-02-29", result.get("date").asText());
        assertEquals("2024-03-01", result.get("endDate").asText());
        assertFalse(result.has("end_date"));
    }

    @Test
    void 拒绝不存在的日期和带时刻的输入() {
        for (String value : List.of("2026-02-30", "2026-11-16 08:00:00", "2026-11-16T08:00:00")) {
            for (String field : List.of("date", "endDate")) {
                String payload = "{\"" + field + "\":\"" + value + "\"}";
                assertThrows(JsonProcessingException.class,
                        () -> json.readValue(payload, MilestoneCreateReq.class), payload);
                assertThrows(JsonProcessingException.class,
                        () -> json.readValue(payload, MilestoneUpdateReq.class), payload);
            }
        }
    }

    @Test
    void 更新区分省略结束日期与显式清空() throws Exception {
        var omitted = json.readValue("{\"id\":\"1\"}", MilestoneUpdateReq.class);
        var cleared = json.readValue("{\"id\":\"1\",\"endDate\":null}", MilestoneUpdateReq.class);
        var changed = json.readValue("{\"id\":\"1\",\"endDate\":\"2026-11-16\"}", MilestoneUpdateReq.class);
        assertFalse(omitted.isEndDateProvided());
        assertTrue(cleared.isEndDateProvided());
        assertNull(cleared.getEndDate());
        assertTrue(changed.isEndDateProvided());
        assertEquals(LocalDate.of(2026, 11, 16), changed.getEndDate());
        assertFalse(json.readTree(json.writeValueAsString(changed)).has("endDateProvided"));
    }
}
