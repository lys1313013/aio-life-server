package top.aiolife.record.prediction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevCategoryClientTest {
    private JevCategoryClient client;
    private MockRestServiceServer server;
    private ObjectNode request;
    private ObjectNode response;

    @BeforeEach
    void setup() throws Exception {
        client = new JevCategoryClient("test-key", true, "jev-latest", 1000);
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        var scenario = JevScenarioSupport.scenarios().getFirst();
        request = JevScenarioSupport.request(scenario);
        response = JevScenarioSupport.mockResponse(scenario);
    }

    @org.junit.jupiter.api.AfterEach
    void cleanup() { client.close(); }

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
    }

    @Test
    void testPredict_通过真实客户端校验契约并返回字符串ID对应分类() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json(request.toString()))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        assertEquals(104L, client.predict(request));
        server.verify();
    }

    @Test
    void testPredict_低置信度回退() {
        ((ObjectNode) response.path("answers").path("current_category")).put("confidence", 0.64);
        server.expect(requestTo(JevCategoryClient.ENDPOINT))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        assertNull(client.predict(request));
        server.verify();
    }

    @Test
    void testPredict_鉴权失败回退且不重试() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertNull(client.predict(request));
        server.verify();
    }

    @Test
    void testPredict_超时回退且不重试() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withException(new SocketTimeoutException()));
        assertNull(client.predict(request));
        server.verify();
    }

    @Test
    void testPredict_损坏JSON回退() {
        server.expect(requestTo(JevCategoryClient.ENDPOINT)).andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        assertNull(client.predict(request));
        server.verify();
    }

    @Test
    void testPredict_无Key或关闭不请求上游() {
        assertNull(new JevCategoryClient("", true, "jev-latest", 1000).predict(request));
        assertNull(new JevCategoryClient("test-key", false, "jev-latest", 1000).predict(request));
        server.verify();
    }

    @Test
    void testPredict_请求过大不发送() {
        request.put("extra", "测".repeat(64 * 1024));
        assertNull(client.predict(request));
        server.verify();
    }
}
