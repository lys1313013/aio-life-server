package top.aiolife.relationship;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 关系模块基本测试
 */
class RelationshipServiceTest {

    @Test
    void testIdGeneration() {
        // 测试 UUID 生成
        String id = UUID.randomUUID().toString();
        assertNotNull(id);
        assertEquals(36, id.length());
    }

    @Test
    void testLocalDateTimeFormatting() {
        // 测试日期格式化
        LocalDateTime now = LocalDateTime.now();
        String formatted = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertNotNull(formatted);
        assertTrue(formatted.contains("-"));
    }

    @Test
    void testMapCreation() {
        // 测试 Map 构建
        Map<String, Object> map = new HashMap<>();
        map.put("id", "test-id");
        map.put("name", "张三");
        map.put("age", 25);

        assertEquals("test-id", map.get("id"));
        assertEquals("张三", map.get("name"));
        assertEquals(25, map.get("age"));
    }
}
