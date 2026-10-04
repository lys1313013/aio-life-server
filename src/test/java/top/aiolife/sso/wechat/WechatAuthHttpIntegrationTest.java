package top.aiolife.sso.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.AioLifeMain;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实路由、认证拦截器、MySQL、Redis、Sa-Token 与 JSON；只模拟微信外部接口。 */
@ActiveProfiles("test")
@SpringBootTest(classes = AioLifeMain.class)
@AutoConfigureMockMvc
@Transactional
class WechatAuthHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean WechatMiniClient client;

    @Test void 注册到业务访问及首次设密的完整接口契约() throws Exception {
        String openid = "http_" + UUID.randomUUID();
        when(client.enabled()).thenReturn(true);
        when(client.exchangeLogin("http-login-code")).thenReturn(new WechatMiniClient.Identity(openid, null));
        when(client.exchangePhone("http-phone-code")).thenReturn(new WechatMiniClient.Phone("86", "13900139998"));
        mvc.perform(get("/auth/wechat/mini/capabilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        JsonNode pending = postJson("/auth/wechat/mini/login", Map.of("loginCode", "http-login-code"), null);
        assertEquals("PHONE_REQUIRED", pending.path("status").asText());
        assertTrue(pending.path("expiresIn").isInt(), "期限必须是数字，不能被全局 Long 序列化器转换成字符串");
        assertTrue(pending.path("accessToken").isNull());
        String ticket = pending.path("loginTicket").asText();
        mvc.perform(get("/user/info").header("Authorization", "Bearer " + ticket)).andExpect(status().isUnauthorized());
        JsonNode login = postJson("/auth/wechat/mini/phone-login", Map.of("loginTicket", ticket, "phoneCode", "http-phone-code"), null);
        assertEquals("LOGGED_IN", login.path("status").asText());
        assertTrue(login.path("id").isTextual());
        assertFalse(login.path("hasPassword").asBoolean());
        String token = login.path("accessToken").asText();
        mvc.perform(get("/user/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.wechatBound").value(true))
                .andExpect(jsonPath("$.data.hasPassword").value(false))
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.wechatOpenid").doesNotExist());
        postJson("/auth/wechat/mini/password", Map.of("loginCode", "http-login-code", "newPassword", "fixture-new-password"), token);
        mvc.perform(get("/user/info").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
        JsonNode repeated = postJson("/auth/wechat/mini/login", Map.of("loginCode", "http-login-code"), null);
        assertEquals(login.path("id"), repeated.path("id"));
        assertTrue(repeated.path("hasPassword").asBoolean());
        verify(client, times(1)).exchangePhone(anyString());
        mvc.perform(post("/auth/wechat/mini/phone-login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("loginTicket", ticket, "phoneCode", "http-phone-code"))))
                .andExpect(status().isBadRequest());
        verify(client, times(1)).exchangePhone(anyString());
    }

    @Test void 绑定和设密接口不能匿名访问() throws Exception {
        mvc.perform(post("/auth/wechat/mini/bind").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginTicket\":\"ticket\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/wechat/mini/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginCode\":\"code\",\"newPassword\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
    }

    @Test void 无手机号权限也可微信注册访问业务并再次登录同一账号() throws Exception {
        String openid = "no_phone_" + UUID.randomUUID();
        when(client.enabled()).thenReturn(true);
        when(client.exchangeLogin("register-code")).thenReturn(new WechatMiniClient.Identity(openid, null));
        mvc.perform(get("/auth/wechat/mini/capabilities"))
                .andExpect(jsonPath("$.data.registrationEnabled").value(true));
        JsonNode pending = postJson("/auth/wechat/mini/login", Map.of("loginCode", "register-code"), null);
        String ticket = pending.path("loginTicket").asText();
        JsonNode login = postJson("/auth/wechat/mini/register", Map.of("loginTicket", ticket), null);
        assertEquals("LOGGED_IN", login.path("status").asText());
        assertTrue(login.path("newUser").asBoolean());
        assertTrue(login.path("id").isTextual());
        mvc.perform(get("/user/info").header("Authorization", "Bearer " + login.path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.wechatBound").value(true))
                .andExpect(jsonPath("$.data.phoneMasked").isEmpty());
        JsonNode repeated = postJson("/auth/wechat/mini/login", Map.of("loginCode", "register-code"), null);
        assertEquals(login.path("id"), repeated.path("id"));
        assertFalse(repeated.path("newUser").asBoolean());
        mvc.perform(post("/auth/wechat/mini/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("loginTicket", ticket))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/wechat/mini/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginTicket\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("100400"));
        verify(client, never()).exchangePhone(anyString());
    }

    private JsonNode postJson(String path, Object body, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        JsonNode response = json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertEquals("0", response.path("rscode").asText(), response.toString());
        return response.path("data");
    }
}
