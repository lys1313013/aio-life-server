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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** 有界同步调用；上游失败仅使本次推荐回退，不重试、不记录凭证或生活记录。 */
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
            @Value("${aio.life.server.typesafe.request-timeout-ms:1000}") long requestTimeoutMs) {
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

    public Long predict(ObjectNode request) {
        if (!isAvailable()) return null;
        request.put("model", model);
        String body = request.toString();
        if (body.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) return null;
        Future<Long> prediction;
        try {
            prediction = executor.submit(() -> callJev(request, body));
        } catch (RejectedExecutionException e) {
            log.debug("Jev category capacity reached, falling back");
            return null;
        }
        try {
            return prediction.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            prediction.cancel(true);
            log.debug("Jev category exceeded {} ms, falling back", requestTimeoutMs);
            return null;
        } catch (InterruptedException e) {
            prediction.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("Jev category failed, falling back ({})", e.getCause().getClass().getSimpleName());
            return null;
        }
    }

    private Long callJev(ObjectNode request, String body) {
        long started = System.nanoTime();
        try {
            JsonNode response = client.post().uri(ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            var decision = JevCategoryProtocol.decision(response,
                    request.path("questions").path("current_category").path("criteria"));
            log.debug("Jev category decision: {}, elapsedMs={}", decision.reason(),
                    (System.nanoTime() - started) / 1_000_000);
            return decision.categoryId() == null ? null : Long.valueOf(decision.categoryId());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Jev category unavailable, falling back ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
