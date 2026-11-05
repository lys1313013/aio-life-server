package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.record.config.GoalProgressProperties;
import top.aiolife.core.exception.SecondaryLockRequiredException;

/** 仅向部署指定 Query 网关转发当前请求凭据，禁止直接调用 Hasura 管理入口。 */
@Component
@RequiredArgsConstructor
public class GoalQueryClient {
    private final GoalProgressProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public JsonNode execute(String query, JsonNode variables, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("需要当前登录凭据");
        var pending = http.sendAsync(HttpRequest.newBuilder(properties.getQueryUrl())
                .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(query, variables))).build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            var response = pending.get(12, TimeUnit.SECONDS);
            if (response.body().length > 1_000_000) throw new IllegalArgumentException("查询结果过大");
            JsonNode result = json.readTree(response.body());
            if (response.statusCode() != 200 || result == null || result.has("errors") || !result.path("data").isObject()) {
                String code = result == null ? "" : result.path("errors").path(0).path("extensions").path("code").asText();
                if ("SECONDARY_LOCK_REQUIRED".equals(code)) {
                    String menuPath = result.path("errors").path(0).path("extensions").path("menuPath").asText();
                    if (menuPath.matches("/[A-Za-z0-9/_-]{1,200}")) throw new SecondaryLockRequiredException(menuPath);
                    throw new IllegalArgumentException("请先解锁查询所需业务及 AI 工具");
                }
                throw new IllegalArgumentException("查询失败，请检查查询、变量、权限及 Query 服务");
            }
            return result.path("data");
        } catch (IllegalArgumentException | SecondaryLockRequiredException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("查询已中断"); }
        catch (Exception error) { throw new IllegalArgumentException("Query 服务不可用或查询超时"); }
        finally { pending.cancel(true); }
    }

    private String body(String query, JsonNode variables) {
        try { return json.writeValueAsString(Map.of("query", query, "variables", variables)); }
        catch (Exception error) { throw new IllegalArgumentException("查询参数无法序列化"); }
    }
}
