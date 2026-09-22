package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/** 有界同步调用；上游失败仅使本次推荐回退，不重试。记录完整请求参数，不记录鉴权凭证。 */
@Slf4j
@Component
public class JevCategoryClient {
    static final String ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;
    private final String model;
    private final long requestTimeoutMs;
    // 无队列：负载过高时直接回退，不把请求排队到超时之后。
    private final ExecutorService executor = new ThreadPoolExecutor(0, 8, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon(true).name("jev-category-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public JevCategoryClient(
            @Value("${aio.life.server.typesafe.api-key:}") String apiKey,
            @Value("${aio.life.server.typesafe.enabled:true}") boolean enabled,
            @Value("${aio.life.server.typesafe.model:jev-latest}") String model,
            @Value("${aio.life.server.typesafe.request-timeout-ms:1500}") long requestTimeoutMs) {
        if (requestTimeoutMs <= 0 || requestTimeoutMs > Integer.MAX_VALUE)
            throw new IllegalArgumentException("request-timeout-ms must be positive and fit in an integer");
        this.requestTimeoutMs = requestTimeoutMs;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(requestTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(requestTimeoutMs));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.model = model;
    }

    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String unavailableReason() {
        return !enabled ? "DISABLED" : !isAvailable() ? "MISSING_API_KEY" : null;
    }

    private record Outcome(Long categoryId, String reason, Integer httpStatus,
                           String responseModel, Double confidence, String errorType) {
        static Outcome fallback(String reason, Integer status, Throwable error) {
            Throwable cause = error;
            while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return new Outcome(null, reason, status, null, null,
                    cause == null ? null : cause.getClass().getSimpleName());
        }
    }

    public Long predict(ObjectNode request) {
        String callId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        if (!isAvailable()) {
            log.info("Jev category skipped: callId={}, reason={}", callId, unavailableReason());
            return null;
        }
        request.put("model", model);
        String body = request.toString();
        int requestBytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (requestBytes > 64 * 1024) {
            log.warn("Jev category skipped: callId={}, reason=REQUEST_TOO_LARGE, requestBytes={}, limitBytes={}",
                    callId, requestBytes, 64 * 1024);
            return null;
        }
        log.info("Jev category started: callId={}, endpoint={}, model={}, timeoutMs={}, requestBytes={}, "
                        + "categoryCount={}, todayRecordCount={}, referenceRecordCount={}, requestBody={}",
                callId, ENDPOINT, safeLogValue(model), requestTimeoutMs, requestBytes,
                request.path("questions").path("current_category").path("criteria").size(),
                request.path("state").path("todayRecords").size(),
                request.path("state").path("previousComparableDay").path("records").size(), body);
        Future<Outcome> prediction;
        try {
            prediction = executor.submit(() -> callJev(request, body));
        } catch (RejectedExecutionException e) {
            return finish(callId, started, Outcome.fallback("CAPACITY_REACHED", null, e));
        }
        try {
            return finish(callId, started, prediction.get(requestTimeoutMs, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            prediction.cancel(true);
            return finish(callId, started, Outcome.fallback("TOTAL_TIMEOUT", null, e));
        } catch (InterruptedException e) {
            prediction.cancel(true);
            Thread.currentThread().interrupt();
            return finish(callId, started, Outcome.fallback("INTERRUPTED", null, e));
        } catch (ExecutionException e) {
            return finish(callId, started, Outcome.fallback("EXECUTION_ERROR", null, e));
        }
    }

    // 仅等待方打印最终结果，保留 HTTP 线程的 traceId/spanId；超时后的迟到响应不再打印成功。
    private Long finish(String callId, long started, Outcome outcome) {
        String template = "Jev category completed: callId={}, outcome={}, reason={}, httpStatus={}, "
                + "responseModel={}, confidence={}, categoryId={}, elapsedMs={}, timeoutMs={}, errorType={}";
        Object[] fields = {callId, outcome.categoryId() == null ? "FALLBACK" : "ACCEPTED", outcome.reason(),
                outcome.httpStatus(), outcome.responseModel(), outcome.confidence(), outcome.categoryId(),
                (System.nanoTime() - started) / 1_000_000, requestTimeoutMs, outcome.errorType()};
        if (outcome.categoryId() == null) log.warn(template, fields);
        else log.info(template, fields);
        return outcome.categoryId();
    }

    private Outcome callJev(ObjectNode request, String body) {
        Integer status = null;
        try {
            var entity = client.post().uri(ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toEntity(JsonNode.class);
            status = entity.getStatusCode().value();
            JsonNode response = entity.getBody();
            var decision = JevCategoryProtocol.decision(response,
                    request.path("questions").path("current_category").path("criteria"));
            JsonNode confidence = response == null ? null
                    : response.path("answers").path("current_category").path("confidence");
            return new Outcome(decision.categoryId() == null ? null : Long.valueOf(decision.categoryId()),
                    decision.reason(), status, response == null ? null : safeLogValue(response.path("model").asText()),
                    confidence != null && confidence.isNumber() && Double.isFinite(confidence.asDouble())
                            ? confidence.asDouble() : null, null);
        } catch (RestClientResponseException e) {
            return Outcome.fallback("HTTP_ERROR", e.getStatusCode().value(), e);
        } catch (RestClientException | IllegalArgumentException e) {
            boolean timeout = e instanceof RestClientException rest && rest.contains(SocketTimeoutException.class);
            return Outcome.fallback(timeout ? "NETWORK_TIMEOUT" : "CLIENT_ERROR", status, e);
        }
    }

    private static String safeLogValue(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), 96)).replaceAll("[^a-zA-Z0-9._:/-]", "_");
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
