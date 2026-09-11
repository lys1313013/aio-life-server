package top.aiolife.record.weread;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 微信读书官方 Skills 网关；仅允许当前功能使用的只读接口。 */
@Component
public class WereadClient {
    private static final Set<String> ENDPOINTS = Set.of("/shelf/sync", "/readdata/detail",
            "/user/notebooks", "/book/bookmarklist", "/review/list/mine", "/book/getprogress");
    private final RestClient client;

    public WereadClient(RestClient.Builder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = builder.clone().requestFactory(factory).build();
    }

    public JsonNode call(String key, String endpoint, Map<String, Object> params) {
        if (!ENDPOINTS.contains(endpoint)) throw new IllegalArgumentException("不支持的微信读书接口");
        var body = new HashMap<>(params);
        body.put("api_name", endpoint);
        body.put("skill_version", "1.0.4");
        try {
            JsonNode result = client.post().uri("https://i.weread.qq.com/api/agent/gateway")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            (request, response) -> { throw new IllegalStateException("微信读书 Key 无效或已过期，请更新同步设置"); })
                    .onStatus(status -> status.value() == 429,
                            (request, response) -> { throw new IllegalStateException("微信读书请求过于频繁，请稍后重试"); })
                    .onStatus(status -> status.isError(),
                            (request, response) -> { throw new IllegalStateException("微信读书暂时不可用，请稍后重试"); })
                    .body(JsonNode.class);
            if (result == null || !result.isObject()) throw new IllegalStateException("微信读书响应格式异常");
            if (result.hasNonNull("errcode") && !"0".equals(result.path("errcode").asText())) {
                // 不透传外部错误文本，避免错误响应包含凭证或内部信息。
                throw new IllegalStateException("微信读书读取失败，请检查 Key 有效性后重试");
            }
            return result;
        } catch (RestClientException e) {
            // 不保留携带外部响应正文的异常链。
            throw new IllegalStateException("连接微信读书失败，请稍后重试");
        }
    }
}
