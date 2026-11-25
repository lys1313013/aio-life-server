package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** 查询多邻国国际站公开的连胜天数和结束日期。 */
@Component
public class DuolingoClient {
    private final RestClient client;

    public DuolingoClient() {
        var timeout = Duration.ofSeconds(5);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        client = RestClient.builder().baseUrl("https://www.duolingo.com").requestFactory(factory).build();
    }

    public Optional<Streak> getStreak(String username) {
        JsonNode result = client.get().uri("/2017-06-30/users?username={username}&fields={fields}",
                username, "users{streak,streakData{currentStreak{endDate}}}").retrieve().body(JsonNode.class);
        return parseStreak(result);
    }

    static Optional<Streak> parseStreak(JsonNode result) {
        if (result == null || !result.path("users").isArray()) {
            throw new IllegalStateException("多邻国账号数据异常");
        }
        var users = result.get("users");
        if (users.isEmpty()) {
            return Optional.empty();
        }
        var user = users.path(0);
        var streak = user.path("streak");
        if (!streak.isIntegralNumber() || !streak.canConvertToInt() || streak.intValue() < 0) {
            throw new IllegalStateException("多邻国连胜数据异常");
        }
        var endDate = user.path("streakData").path("currentStreak").path("endDate");
        return Optional.of(new Streak(streak.intValue(), parseEndDate(endDate)));
    }

    private static LocalDate parseEndDate(JsonNode endDate) {
        if (!endDate.isTextual()) {
            return null;
        }
        try {
            return LocalDate.parse(endDate.textValue());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record Streak(int days, LocalDate endDate) {
    }
}
