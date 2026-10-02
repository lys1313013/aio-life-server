package top.aiolife.sso.wechat;

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
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/jscode2session?")))
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

    @Test void 应用token与手机号均兼容textPlain类型() {
        when(redis.get("auth:wechat:token:wx_test")).thenReturn(null);
        when(locks.tryRun(anyString(), any())).thenAnswer(inv -> { inv.<Runnable>getArgument(1).run(); return true; });
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andRespond(withSuccess("{\"access_token\":\"app-token\",\"expires_in\":7200}", MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=app-token"))
                .andRespond(withSuccess("""
                    {"errcode":0,"phone_info":{"countryCode":"86","purePhoneNumber":"13800138000",
                    "watermark":{"appid":"wx_test","timestamp":%d}}}
                    """.formatted(Instant.now().getEpochSecond()), MediaType.TEXT_PLAIN));
        assertEquals(new WechatMiniClient.Phone("86", "13800138000"), client.exchangePhone("phone-code"));
        server.verify();
    }

    @Test void 非JSON响应不向调用方暴露上游正文() {
        server.expect(anything()).andRespond(withSuccess("<html>secret-detail</html>", MediaType.TEXT_HTML));
        var error = assertThrows(ResponseStatusException.class, () -> client.exchangeLogin("login-code"));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getMessage().contains("secret-detail"));
        server.verify();
    }

    @Test void 手机号使用独立code并拆分区号() {
        phoneResponse("wx_test", "44", "7700900123", 0);
        assertEquals(new WechatMiniClient.Phone("44", "7700900123"), client.exchangePhone("phone-code"));
        server.verify();
    }

    @Test void 拒绝其他小程序的手机号水印() {
        phoneResponse("wx_other", "86", "13800138000", 0);
        assertThrows(ResponseStatusException.class, () -> client.exchangePhone("phone-code"));
    }

    @Test void 拒绝过期水印和非标准号码() {
        phoneResponse("wx_test", "86", "13800138000", -400);
        assertThrows(ResponseStatusException.class, () -> client.exchangePhone("phone-code"));
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

    @Test void 缓存应用token并提前过期() {
        when(redis.get("auth:wechat:token:wx_test")).thenReturn(null);
        when(locks.tryRun(anyString(), any())).thenAnswer(inv -> { inv.<Runnable>getArgument(1).run(); return true; });
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andExpect(method(HttpMethod.POST)).andExpect(jsonPath("$.force_refresh").value(false))
                .andRespond(withSuccess("{\"access_token\":\"app-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        phoneResponse("wx_test", "86", "13800138000", 0);
        client.exchangePhone("phone-code");
        verify(redis).set("auth:wechat:token:wx_test", "app-token", 7140, java.util.concurrent.TimeUnit.SECONDS);
        server.verify();
    }

    @Test void 应用token过期只清除当前缓存且不重放手机号code() {
        server.expect(anything()).andRespond(withSuccess("{\"errcode\":42001}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> client.exchangePhone("phone-code"));
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
}
