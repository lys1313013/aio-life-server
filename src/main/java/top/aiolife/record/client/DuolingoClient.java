package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.OptionalInt;

/** 仅查询多邻国国际站公开的连胜天数。 */
@Component
public class DuolingoClient {
    private final RestClient client;

    public DuolingoClient() {
        var timeout = Duration.ofSeconds(5);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        client = RestClient.builder().baseUrl("https://www.duolingo.com").requestFactory(factory).build();
    }

    public OptionalInt getStreak(String username) {
        JsonNode result = client.get().uri("/2017-06-30/users?username={username}&fields={fields}",
                username, "users{streak}").retrieve().body(JsonNode.class);
        return parseStreak(result);
    }

    static OptionalInt parseStreak(JsonNode result) {
        if (result == null || !result.path("users").isArray()) {
            throw new IllegalStateException("多邻国账号数据异常");
        }
        var users = result.get("users");
        if (users.isEmpty()) {
            return OptionalInt.empty();
        }
        var streak = users.path(0).path("streak");
        if (!streak.isIntegralNumber() || !streak.canConvertToInt() || streak.intValue() < 0) {
            throw new IllegalStateException("多邻国连胜数据异常");
        }
        return OptionalInt.of(streak.intValue());
    }
}
