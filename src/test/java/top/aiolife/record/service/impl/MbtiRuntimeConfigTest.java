package top.aiolife.record.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import top.aiolife.system.service.IntegrationSystemConfigService;
import top.aiolife.system.service.IntegrationSystemConfigService.MbtiSnapshot;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MbtiRuntimeConfigTest {
    @Test void testCall_下一次请求使用新地址密钥且清除后停止调用() {
        var config = mock(IntegrationSystemConfigService.class);
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        var service = new MbtiResultServiceImpl(http, config);
        when(config.mbti()).thenReturn(new MbtiSnapshot("first-key", "https://first.example.com"));
        server.expect(requestTo("https://first.example.com/v1/new_test?api_key=first-key&lang=cn"))
                .andRespond(withSuccess("{\"data\":{\"test_id\":\"first-test\",\"test_url\":\"https://test.example.com\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://second.example.com/v1/check_test?api_key=second%26key&test_id=test%26id"))
                .andRespond(withSuccess("{\"data\":{\"prediction\":\"INTJ\"}}", MediaType.APPLICATION_JSON));
        assertEquals("first-test", service.createTest().get("testId"));
        when(config.mbti()).thenReturn(new MbtiSnapshot("second&key", "https://second.example.com"));
        assertEquals("INTJ", service.checkTest("test&id").get("mbtiType"));
        when(config.mbti()).thenReturn(new MbtiSnapshot("", "https://second.example.com"));
        assertEquals(false, service.createTest().get("success"));
        assertEquals(false, service.checkTest("any").get("success"));
        server.verify();
    }

    @Test void testError_上游错误不泄露携带密钥的请求地址() {
        var config = mock(IntegrationSystemConfigService.class);
        when(config.mbti()).thenReturn(new MbtiSnapshot("synthetic-secret", "https://api.example.com"));
        var http = new RestTemplate();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(anything()).andRespond(request -> { throw new IOException(request.getURI().toString()); });
        var response = new MbtiResultServiceImpl(http, config).createTest();
        assertEquals(false, response.get("success"));
        assertFalse(response.toString().contains("synthetic-secret"));
        server.verify();
    }
}
