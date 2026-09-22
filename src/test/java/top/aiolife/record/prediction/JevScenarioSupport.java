package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import top.aiolife.system.service.IWorkCalendarService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 复用生产请求/响应契约的测试夹具，以及显式开启的真实调用探针。 */
final class JevScenarioSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String ENDPOINT = "https://api.typesafe.ai/v1/systemone";

    record Decision(String categoryId, String reason) {}

    static List<JsonNode> scenarios() throws IOException {
        try (var input = JevScenarioSupport.class.getResourceAsStream("/jev/time-category-scenarios.json")) {
            if (input == null) throw new IOException("Missing Jev scenario fixtures");
            List<JsonNode> result = new ArrayList<>();
            JSON.readTree(input).forEach(result::add);
            return result;
        }
    }

    static ObjectNode request(JsonNode scenario) {
        return request(scenario, JevCalendarFixture.calendar());
    }

    static ObjectNode request(JsonNode scenario, IWorkCalendarService calendar) {
        return JevCategoryProtocol.request(scenario, calendar);
    }

    static JsonNode evaluate(RestClient client, String key, ObjectNode request) {
        return client.post().uri(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().body(JsonNode.class);
    }

    static Decision decision(JsonNode response, JsonNode criteria) {
        var decision = JevCategoryProtocol.decision(response, criteria);
        return new Decision(decision.categoryId(), decision.reason());
    }

    static ObjectNode mockResponse(JsonNode scenario) {
        ObjectNode response = JSON.createObjectNode().put("model", "mock-only");
        var answer = response.putObject("answers").putObject("current_category");
        String choice = scenario.required("expectedCategoryId").asText();
        answer.put("type", "choice").put("choice", choice).put("confidence", 1.0);
        var probabilities = answer.putObject("probabilities");
        scenario.required("categories").fieldNames()
                .forEachRemaining(key -> probabilities.put(key, key.equals(choice) ? 1.0 : 0.0));
        response.putObject("usage").put("input_tokens", 0).put("output_tokens", 0);
        return response;
    }
}
