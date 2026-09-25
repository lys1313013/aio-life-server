package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevCategoryClientTest {
    private JevCategoryClient client;
    private MockRestServiceServer server;
    private ObjectNode request;
    private ObjectNode response;
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private Logger logger;
    private Level previousLevel;
    private AbstractAppender appender;

    @BeforeEach
    void setup() throws Exception {
        client = new JevCategoryClient("test-key", true, "jev-latest", 1000);
        logger = (Logger) LogManager.getLogger(JevCategoryClient.class);
        previousLevel = logger.getLevel();
        appender = new AbstractAppender("jev-test", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        var scenario = JevScenarioSupport.scenarios().getFirst();
        request = JevScenarioSupport.request(scenario);
        response = JevScenarioSupport.mockResponse(scenario);
    }

    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        client.close();
        logger.removeAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
        MDC.remove("traceId");
    }

    private String logs() {
        return events.stream().map(event -> event.getMessage().getFormattedMessage())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test
    void testPredict_总等待超过配置上限立即回退() {
        client.close();
        client = new JevCategoryClient("test-key", true, "jev-latest", 50);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(req -> {
            java.util.concurrent.locks.LockSupport.parkNanos(java.time.Duration.ofSeconds(2).toNanos());
            throw new java.io.IOException("simulated delay");
        });
        long started = System.nanoTime();
        assertNull(client.predict(request));
        assertTrue((System.nanoTime() - started) / 1_000_000 < 500,
                "等待必须受总时限约束，不能等到上游响应结束");
        assertTrue(logs().contains("原因=TOTAL_TIMEOUT"));
        assertTrue(logs().matches("(?s).*耗时毫秒=\\d+.*"));
    }

    @Test
    void testPredict_通过真实客户端校验契约并返回字符串ID对应分类() {
        MDC.put("traceId", "jev-test-trace");
        response.put("model", "jev-1.13.0");
        // 模型应以客户端配置为准，日志中的 JSON 必须与实际发送的 JSON 完全一致。
        request.put("model", "overridden-model");
        server.expect(requestTo(JevCategoryClient.ENDPOINT))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(req -> {
                    String sentBody = ((org.springframework.mock.http.client.MockClientHttpRequest) req).getBodyAsString();
                    String loggedBody = events.stream().map(event -> event.getMessage().getFormattedMessage())
                            .filter(message -> message.contains("请求体="))
                            .findFirst().orElseThrow().split("请求体=", 2)[1];
                    assertEquals(sentBody, loggedBody);
                    assertEquals("jev-latest", new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(loggedBody).path("model").asText());
                })
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        assertEquals(104L, client.predict(request));
        server.verify();
        String logs = logs();
        assertTrue(logs.contains("结果=ACCEPTED, 原因=PREDICTED, HTTP状态=200"));
        assertTrue(logs.contains("响应模型=jev-1.13.0"));
        assertTrue(logs.contains("分类ID=104"));
        assertTrue(logs.matches("(?s).*耗时毫秒=\\d+.*"));
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(event -> "jev-test-trace".equals(event.getContextData().getValue("traceId"))));
        var ids = events.stream().map(event -> event.getMessage().getFormattedMessage()
                .split("调用ID=")[1].split(",")[0]).distinct().toList();
        assertEquals(1, ids.size(), "开始和完成日志使用同一个调用 ID");
        assertFalse(logs.contains("test-key"));
        assertFalse(logs.contains("Authorization"));
        assertTrue(logs.contains("请求体=" + request));
        assertTrue(logs.contains("todayRecords"));
    }

    @Test
    void testPredict_低置信度回退() {
        ((ObjectNode) response.path("answers").path("current_category")).put("confidence", 0.64);
        server.expect(requestTo(JevCategoryClient.ENDPOINT))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        assertNull(client.predict(request));
        server.verify();
        assertTrue(logs().contains("结果=FALLBACK, 原因=LOW_CONFIDENCE, HTTP状态=200"));
        assertTrue(logs().contains("置信度=0.64"));
    }

    @Test
    void testPredict_鉴权失败回退且不重试() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("private-upstream-body test-key"));
        assertNull(client.predict(request));
        server.verify();
        assertTrue(logs().contains("原因=HTTP_ERROR, HTTP状态=401"));
        assertTrue(logs().matches("(?s).*耗时毫秒=\\d+.*"));
        assertFalse(logs().contains("private-upstream-body"));
        assertFalse(logs().contains("test-key"));
    }

    @Test
    void testPredict_超时回退且不重试() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withException(new SocketTimeoutException()));
        assertNull(client.predict(request));
        server.verify();
        assertTrue(logs().contains("原因=NETWORK_TIMEOUT"));
        assertTrue(logs().contains("错误类型=SocketTimeoutException"));
    }

    @Test
    void testPredict_损坏JSON回退() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        assertNull(client.predict(request));
        server.verify();
        assertTrue(logs().contains("原因=CLIENT_ERROR"));
    }

    @Test
    void testPredict_无Key或关闭不请求上游() {
        assertNull(new JevCategoryClient("", true, "jev-latest", 1000).predict(request));
        assertNull(new JevCategoryClient("test-key", false, "jev-latest", 1000).predict(request));
        server.verify();
        assertTrue(logs().contains("原因=MISSING_API_KEY"));
        assertTrue(logs().contains("原因=DISABLED"));
    }

    @Test
    void testPredict_请求过大不发送() {
        request.put("extra", "测".repeat(64 * 1024));
        assertNull(client.predict(request));
        server.verify();
        assertTrue(logs().contains("原因=REQUEST_TOO_LARGE"));
    }

    @Test
    void testPredict_超时后迟到成功不能打印已采用() throws Exception {
        client.close();
        client = new JevCategoryClient("test-key", true, "jev-latest", 100);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(req -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try { release.await(); }
                catch (InterruptedException ignored) { /* 模拟底层 I/O 无法立即取消。 */ }
            }
            finished.countDown();
            return withSuccess(response.toString(), MediaType.APPLICATION_JSON).createResponse(req);
        });
        try {
            assertNull(client.predict(request));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(finished.await(1, TimeUnit.SECONDS));
            client.close();
            var executor = (java.util.concurrent.ExecutorService) ReflectionTestUtils.getField(client, "executor");
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
            assertTrue(logs().contains("原因=TOTAL_TIMEOUT"));
            assertFalse(logs().contains("结果=ACCEPTED"));
            assertEquals(1, events.stream().filter(event -> event.getMessage().getFormattedMessage()
                    .contains("Jev 分类调用完成")).count());
        } finally {
            release.countDown();
        }
    }
}
