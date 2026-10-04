package top.aiolife.sso.qr;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.AioLifeMain;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.IApiKeyService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实路由、拦截器、Sa-Token、Redis、MySQL；只使用事务回滚的模拟账号。 */
@ActiveProfiles("test")
@SpringBootTest(classes = AioLifeMain.class, properties = "aio.life.neo4j.enabled=false")
@AutoConfigureMockMvc
@Transactional
class QrLoginHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired QrLoginStore store;
    @Autowired IApiKeyService apiKeys;
    private final List<String> tokens = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private String appToken;
    private Long userId;
    private String ip;

    @BeforeEach void setup() {
        ip = "fixture-" + UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUsername("qr_" + UUID.randomUUID().toString().replace("-", ""));
        user.setNickname("扫码测试账号"); user.setRole("user"); user.setIsDeleted(0);
        users.insert(user); userId = user.getId();
        appToken = session(userId);
    }
    @AfterEach void cleanup() {
        tokens.forEach(StpUtil::logoutByTokenValue);
        for (String id : ids) { var value = store.read(id); if (value != null) store.replace(id, value, null, 0); }
    }
    private String session(Long id) {
        String token = StpUtil.getStpLogic().createLoginSession(id); tokens.add(token); return token;
    }
    private JsonNode create() throws Exception {
        var result = postJson("", Map.of(), null);
        ids.add(result.path("id").asText());
        assertTrue(result.path("expiresIn").isInt());
        assertEquals(120, result.path("expiresIn").asInt());
        assertFalse(result.path("qrContent").asText().contains(result.path("browserSecret").asText()));
        return result;
    }
    private Map<String, Object> browser(JsonNode ticket) {
        return Map.of("id", ticket.path("id").asText(), "browserSecret", ticket.path("browserSecret").asText());
    }
    private Map<String, Object> scan(JsonNode ticket) {
        String content = ticket.path("qrContent").asText();
        return Map.of("id", ticket.path("id").asText(), "ticket", content.substring(content.lastIndexOf('=') + 1));
    }
    private Map<String, Object> decision(JsonNode ticket, boolean approve) {
        Map<String, Object> data = new HashMap<>(scan(ticket)); data.put("approve", approve); return data;
    }
    private JsonNode postJson(String suffix, Object data, String token) throws Exception {
        var request = post("/auth/qr-login" + suffix).with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        // 扫码签发不提前写 Cookie，其他浏览器不能被被动登录。
        assertNull(response.getHeader("Set-Cookie"));
        return json.readTree(response.getContentAsString()).path("data");
    }
    private void expectError(String suffix, Object data, String token, int code) throws Exception {
        var request = post("/auth/qr-login" + suffix).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
        if (token != null) request.header("Authorization", "Bearer " + token);
        mvc.perform(request).andExpect(status().is(code));
    }

    @Test void 扫码确认兑换新会话且响应丢失可重取同一Token() throws Exception {
        var ticket = create();
        expectError("/consume", browser(ticket), null, 409);
        var scanned = postJson("/scan", scan(ticket), appToken);
        assertEquals("SCANNED", scanned.path("status").asText());
        assertEquals(ticket.path("verificationCode"), scanned.path("verificationCode"));
        expectError("/consume", browser(ticket), null, 409);
        postJson("/decision", decision(ticket, true), appToken);
        postJson("/decision", decision(ticket, true), appToken);
        var login = postJson("/consume", browser(ticket), null);
        String webToken = login.path("accessToken").asText(); tokens.add(webToken);
        assertNotEquals(appToken, webToken); assertTrue(login.path("id").isTextual());
        assertEquals(webToken, postJson("/consume", browser(ticket), null).path("accessToken").asText());
        assertEquals(userId.toString(), StpUtil.getLoginIdByToken(webToken).toString());
        assertNotNull(StpUtil.getLoginIdByToken(appToken));
        mvc.perform(get("/auth/qr-login/" + ticket.path("id").asText() + "/status")
                .header("X-QR-Secret", ticket.path("browserSecret").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONSUMED"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
        postJson("/cancel", browser(ticket), null);
        assertNotNull(StpUtil.getLoginIdByToken(webToken));
    }

    @Test void 扫码凭证不可用于查询兑换且其他会话不能接管确认() throws Exception {
        var ticket = create();
        var stolen = Map.of("id", ticket.path("id").asText(), "browserSecret", scan(ticket).get("ticket"));
        expectError("/consume", stolen, null, 403);
        mvc.perform(get("/auth/qr-login/" + ticket.path("id").asText() + "/status")
                .header("X-QR-Secret", scan(ticket).get("ticket"))).andExpect(status().isForbidden());
        expectError("/scan", scan(ticket), null, 401);
        expectError("/decision", decision(ticket, true), null, 401);
        postJson("/scan", scan(ticket), appToken);
        String anotherSession = session(userId);
        expectError("/scan", scan(ticket), anotherSession, 403);
        expectError("/decision", decision(ticket, true), anotherSession, 403);
        mvc.perform(post("/auth/qr-login/decision").cookie(new Cookie("Authorization", "Bearer " + appToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(decision(ticket, true))))
                .andExpect(status().isForbidden());
    }

    @Test void 拒绝取消过期及授权会话撤销后不能兑换() throws Exception {
        var rejected = create();
        postJson("/scan", scan(rejected), appToken);
        postJson("/decision", decision(rejected, false), appToken);
        expectError("/decision", decision(rejected, true), appToken, 409);
        expectError("/consume", browser(rejected), null, 409);
        var cancelled = create();
        postJson("/cancel", browser(cancelled), null);
        expectError("/scan", scan(cancelled), appToken, 400);
        var expired = create();
        var before = store.read(expired.path("id").asText()); var t = before.ticket();
        store.replace(expired.path("id").asText(), before, new QrLoginModels.Ticket(t.browserHash(), t.scanHash(), t.status(),
                t.browser(), t.requestedAt(), t.verificationCode(), 1, null, null, null), 0);
        expectError("/scan", scan(expired), appToken, 400);
        var revoked = create(); postJson("/scan", scan(revoked), appToken);
        postJson("/decision", decision(revoked, true), appToken);
        StpUtil.logoutByTokenValue(appToken);
        expectError("/consume", browser(revoked), null, 400);
    }

    @Test void 账号删除后无法兑换() throws Exception {
        var ticket = create(); postJson("/scan", scan(ticket), appToken);
        postJson("/decision", decision(ticket, true), appToken);
        users.deleteById(userId);
        expectError("/consume", browser(ticket), null, 401);
    }
    @Test void ApiKey不能授权扫码登录() throws Exception {
        var ticket = create();
        var key = apiKeys.generateApiKey(userId, "qr-test", 1);
        expectError("/scan", scan(ticket), key.getApiKey(), 403);
        postJson("/scan", scan(ticket), appToken);
        expectError("/decision", decision(ticket, true), key.getApiKey(), 403);
    }

    @Test void 并发兑换仅签发一次并重取同一结果() throws Exception {
        var ticket = create(); postJson("/scan", scan(ticket), appToken);
        postJson("/decision", decision(ticket, true), appToken);
        var guard = org.mockito.Mockito.mock(top.aiolife.sso.service.AccountStatusGuard.class);
        var userMapper = org.mockito.Mockito.mock(UserMapper.class);
        var sessions = org.mockito.Mockito.mock(top.aiolife.sso.service.LoginSessionService.class);
        var user = new UserEntity(); user.setId(userId);
        org.mockito.Mockito.when(userMapper.selectById(userId)).thenReturn(user);
        var result = new top.aiolife.sso.pojo.vo.UserLoginVO(); result.setAccessToken(session(userId)); result.setId(userId);
        org.mockito.Mockito.when(sessions.completeQr(user, ip)).thenReturn(result);
        var service = new QrLoginService(store, guard, userMapper, sessions);
        var request = new QrLoginModels.BrowserRequest(ticket.path("id").asText(), ticket.path("browserSecret").asText());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 8).<java.util.concurrent.Callable<String>>mapToObj(n -> () -> {
                try { return service.consume(request, ip).getAccessToken(); }
                catch (org.springframework.web.server.ResponseStatusException e) {
                    assertEquals(409, e.getStatusCode().value()); return null;
                }
            }).toList();
            var results = executor.invokeAll(calls);
            int successes = 0;
            for (var value : results) if (value.get() != null) { successes++; assertEquals(result.getAccessToken(), value.get()); }
            assertTrue(successes >= 1);
        }
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.times(1)).completeQr(user, ip);
        assertEquals(result.getAccessToken(), service.consume(request, ip).getAccessToken());
    }

    @Test void 签发失败不可重入创建会话() throws Exception {
        var ticket = create(); postJson("/scan", scan(ticket), appToken);
        postJson("/decision", decision(ticket, true), appToken);
        var guard = org.mockito.Mockito.mock(top.aiolife.sso.service.AccountStatusGuard.class);
        var userMapper = org.mockito.Mockito.mock(UserMapper.class);
        var sessions = org.mockito.Mockito.mock(top.aiolife.sso.service.LoginSessionService.class);
        var user = new UserEntity(); user.setId(userId);
        org.mockito.Mockito.when(userMapper.selectById(userId)).thenReturn(user);
        org.mockito.Mockito.when(sessions.completeQr(user, ip)).thenThrow(new IllegalStateException("fixture failure"));
        var service = new QrLoginService(store, guard, userMapper, sessions);
        var request = new QrLoginModels.BrowserRequest(ticket.path("id").asText(), ticket.path("browserSecret").asText());
        assertThrows(IllegalStateException.class, () -> service.consume(request, ip));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.consume(request, ip));
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.times(1)).completeQr(user, ip);
    }

}
