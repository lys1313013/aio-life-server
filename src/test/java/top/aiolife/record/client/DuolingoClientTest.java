package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuolingoClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parseStreak_区分正数零天和查无账号() throws Exception {
        var active = DuolingoClient.parseStreak(json.readTree("""
                {"users":[{"streak":23,"streakData":{"currentStreak":{"endDate":"2026-11-25"}}}]}
                """)).orElseThrow();
        assertEquals(new DuolingoClient.Streak(23, LocalDate.of(2026, 11, 25)), active);
        var zero = DuolingoClient.parseStreak(json.readTree("""
                {"users":[{"streak":0,"streakData":{"currentStreak":null}}]}
                """)).orElseThrow();
        assertEquals(new DuolingoClient.Streak(0, null), zero);
        assertTrue(DuolingoClient.parseStreak(json.readTree("{\"users\":[]}")).isEmpty());
    }

    @Test
    void parseStreak_日期缺失或无效时保留天数() throws Exception {
        for (String data : new String[]{"{}", "{\"currentStreak\":{\"endDate\":\"2026-02-30\"}}"}) {
            var payload = """
                    {"users":[{"streak":3,"streakData":%s}]}
                    """.formatted(data);
            var streak = DuolingoClient.parseStreak(json.readTree(payload)).orElseThrow();
            assertEquals(3, streak.days());
            assertNull(streak.endDate());
        }
    }

    @Test
    void parseStreak_缺失字段或无效数值不能显示零天() {
        for (String payload : new String[]{"{}", "{\"users\":[{}]}", "{\"users\":[{\"streak\":-1}]}"}) {
            assertThrows(IllegalStateException.class, () -> DuolingoClient.parseStreak(json.readTree(payload)));
        }
    }
}
