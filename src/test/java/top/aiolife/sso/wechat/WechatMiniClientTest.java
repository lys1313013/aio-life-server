package top.aiolife.sso.wechat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.util.RedisUtil;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WechatMiniClientTest {
    WechatMiniClient client;
    RedisUtil redis = mock(RedisUtil.class);
    DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    MockRestServiceServer server;
    WechatMiniProperties properties = new WechatMiniProperties();

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setAppId("wx_test");
        properties.setAppSecret("test-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WechatMiniClient(properties, redis, locks, builder);
        when(redis.get("auth:wechat:token:wx_test")).thenReturn("app-token");
    }

    @Test void 登录只提取身份且允许没有unionid() {
        server.expect(requestTo(Matchers.containsString("/sns/jscode2session?")))
                .andExpect(queryParam("js_code", "login-code"))
                .andRespond(withSuccess("{\"openid\":\"Case_Sensitive-id\",\"session_key\":\"never-return\"}", MediaType.APPLICATION_JSON));
        assertEquals(new WechatMiniClient.Identity("Case_Sensitive-id", null), client.exchangeLogin("login-code"));
        server.verify();
    }

    @Test void 登录兼容微信实际返回的textPlain类型() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"openid\":\"wechat-user\",\"session_key\":\"never-return\"}", MediaType.TEXT_PLAIN));
        assertEquals(new WechatMiniClient.Identity("wechat-user", null), client.exchangeLogin("login-code"));
        server.verify();
    }

    @Test void textPlain错误响应仍能识别一次性凭证失效() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"errcode\":40029,\"errmsg\":\"secret-detail\"}", MediaType.TEXT_PLAIN));
        var error = assertThrows(ResponseStatusException.class, () -> client.exchangeLogin("used"));
        assertEquals(400, error.getStatusCode().value());
        assertFalse(error.getMessage().contains("secret-detail"));
        server.verify();
    }



    @Test void 非JSON响应不向调用方暴露上游正文() {
        server.expect(anything()).andRespond(withSuccess("<html>secret-detail</html>", MediaType.TEXT_HTML));
        var error = assertThrows(ResponseStatusException.class, () -> client.exchangeLogin("login-code"));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getMessage().contains("secret-detail"));
        server.verify();
    }







    @Test void 微信响应错误不泄漏上游内容() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"secret-detail\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(ResponseStatusException.class, () -> client.exchangeLogin("used"));
        assertFalse(error.getMessage().contains("secret-detail"));
        assertTrue(error.getReason().contains("重新微信登录"));
    }

    @Test void 未开启时不请求微信() {
        properties.setEnabled(false);
        assertFalse(client.enabled());
        assertThrows(ResponseStatusException.class, () -> client.exchangeLogin("code"));
        server.verify();
    }





    @Test void 小程序码携带固定页面和随机scene不包含浏览器密钥() {
        byte[] image = {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10};
        server.expect(requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=app-token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.page").value("pages/auth/web-login"))
                .andExpect(jsonPath("$.scene").value("a".repeat(32)))
                .andExpect(jsonPath("$.env_version").value("release"))
                .andExpect(jsonPath("$.browserSecret").doesNotExist())
                .andRespond(withSuccess(image, MediaType.IMAGE_PNG));
        assertTrue(client.webLoginQrCode("a".repeat(32)).startsWith("data:image/png;base64,"));
        server.verify();
    }

    @Test void 取码错误不能当图片返回且失效token会清缓存() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"secret-detail\"}", MediaType.TEXT_PLAIN));
        var error = assertThrows(ResponseStatusException.class, () -> client.webLoginQrCode("b".repeat(32)));
        assertFalse(error.getMessage().contains("secret-detail"));
        verify(redis).unlock("auth:wechat:token:wx_test", "app-token");
        server.verify();
    }

    private void phoneResponse(String appid, String country, String number, int seconds) {
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=app-token"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"code\":\"phone-code\"}"))
                .andRespond(withSuccess("""
                    {"errcode":0,"phone_info":{"countryCode":"%s","purePhoneNumber":"%s",
                    "watermark":{"appid":"%s","timestamp":%d}}}
                    """.formatted(country, number, appid, Instant.now().getEpochSecond() + seconds), MediaType.APPLICATION_JSON));
    }

    @Test void 订阅消息携带模板字段与固定业务页并以微信错误码判断成功() {
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=app-token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.touser").value("fixture-openid"))
                .andExpect(jsonPath("$.template_id").value("fixture-template"))
                .andExpect(jsonPath("$.page").value("pages/coding/leetcode"))
                .andExpect(jsonPath("$.miniprogram_state").value("trial"))
                .andExpect(jsonPath("$.data.thing1.value").value("提醒"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.TEXT_PLAIN));
        assertTrue(subscription().success());
        server.verify();
    }

    @Test void HTTP成功但无订阅权限不能当作发送成功或自动重试() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":43101,\"errmsg\":\"private-detail\"}", MediaType.APPLICATION_JSON));
        var result = subscription();
        assertEquals("43101", result.code());
        assertFalse(result.success());
        assertFalse(result.retryable());
    }

    @Test void 订阅消息超时与缺少错误码均为未知且不得重放() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("private-detail")));
        var result = subscription();
        assertEquals("UNKNOWN", result.code());
        assertFalse(result.retryable());
        server.verify();
    }

    @Test void 非标准JSON不能默认当作errcode零成功() {
        server.expect(anything()).andRespond(withSuccess("{\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));
        assertEquals("UNKNOWN", subscription().code());
    }

    @Test void 订阅消息Token失效清理旧缓存但不在客户端立即重放() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":42001}", MediaType.APPLICATION_JSON));
        var result = subscription();
        assertTrue(result.retryable());
        verify(redis).unlock("auth:wechat:token:wx_test", "app-token");
        server.verify();
    }

    private WechatMiniClient.SubscriptionResult subscription() {
        return client.sendSubscription("fixture-openid", "fixture-template", "trial", Map.of("thing1", Map.of("value", "提醒")));
    }
}
