package top.aiolife.record.pojo.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressStatusEnumTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testJson_使用小写语义Code序列化与反序列化() throws Exception {
        assertEquals("\"in_progress\"",
                objectMapper.writeValueAsString(ProgressStatusEnum.IN_PROGRESS));
        assertEquals(ProgressStatusEnum.IN_PROGRESS,
                objectMapper.readValue("\"in_progress\"", ProgressStatusEnum.class));
    }

    @Test
    void testMybatisValue_使用小写语义Code持久化() {
        assertEquals("on_hold", ProgressStatusEnum.ON_HOLD.getValue());
    }
}
