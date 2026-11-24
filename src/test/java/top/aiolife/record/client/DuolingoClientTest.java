package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class DuolingoClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parseStreak_区分正数零天和查无账号() throws Exception {
        assertEquals(OptionalInt.of(23), DuolingoClient.parseStreak(json.readTree("{\"users\":[{\"streak\":23}]}")));
        assertEquals(OptionalInt.of(0), DuolingoClient.parseStreak(json.readTree("{\"users\":[{\"streak\":0}]}")));
        assertEquals(OptionalInt.empty(), DuolingoClient.parseStreak(json.readTree("{\"users\":[]}")));
    }

    @Test
    void parseStreak_缺失字段或无效数值不能显示零天() {
        for (String payload : new String[]{"{}", "{\"users\":[{}]}", "{\"users\":[{\"streak\":-1}]}"}) {
            assertThrows(IllegalStateException.class, () -> DuolingoClient.parseStreak(json.readTree(payload)));
        }
    }
}
