package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 显式开启才发送六组虚构数据。评估模型实际结果，不把预期类别伪造成模型返回。 */
@EnabledIfSystemProperty(named = "jev.live", matches = "true")
@EnabledIfEnvironmentVariable(named = "AIO_LIFE_TYPESAFE_API_KEY", matches = ".+")
class JevTimeCategoryLiveTest {
    static Stream<JsonNode> scenarios() throws IOException {
        return JevScenarioSupport.scenarios().stream();
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void testLive_记录真实分类置信度耗时与人工预期差异(JsonNode scenario) throws IOException {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        var client = RestClient.builder().requestFactory(factory).build();
        var request = JevScenarioSupport.request(scenario);
        long started = System.nanoTime();
        JsonNode response;
        try {
            response = JevScenarioSupport.evaluate(client, System.getenv("AIO_LIFE_TYPESAFE_API_KEY"), request);
        } catch (RestClientResponseException e) {
            // 不输出上游正文或认证信息。
            fail("Jev HTTP " + e.getStatusCode().value() + "，样例 " + scenario.path("id").asText());
            return;
        } catch (org.springframework.web.client.RestClientException e) {
            fail("Jev 网络调用失败，样例 " + scenario.path("id").asText());
            return;
        }
        var decision = JevScenarioSupport.decision(response, scenario.path("categories"));
        var report = JevScenarioSupport.JSON.createObjectNode();
        report.put("mode", "LIVE");
        report.put("calendarSource", "IWorkCalendarService / repository 2026 calendar fixture");
        report.put("scenario", scenario.path("id").asText());
        report.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
        report.set("request", request);
        report.set("response", response);
        report.put("expectedCategoryId", scenario.path("expectedCategoryId").asText());
        report.put("matchesExpected", scenario.path("expectedCategoryId").asText()
                .equals(response == null ? "" : response.path("answers").path("current_category").path("choice").asText()));
        report.put("decision", decision.reason());
        report.put("acceptedCategoryId", decision.categoryId());
        Path output = Path.of("target/jev-calendar-live", scenario.path("id").asText() + ".json");
        Files.createDirectories(output.getParent());
        JevScenarioSupport.JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertNotEquals("INVALID_RESPONSE", decision.reason(), "响应不符合契约，参见 " + output);
        // 预期标签用于评估，模型不同意不等于 HTTP 契约失败；差异保存在报告中。
    }
}
