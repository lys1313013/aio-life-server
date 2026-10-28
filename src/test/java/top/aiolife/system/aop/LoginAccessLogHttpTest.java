package top.aiolife.system.aop;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.sso.api.QrLoginController;
import top.aiolife.sso.api.WechatAuthController;
import top.aiolife.sso.api.WechatWebLoginController;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.qr.QrLoginModels.Ticket;
import top.aiolife.sso.qr.QrLoginService;
import top.aiolife.sso.qr.QrLoginStore;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.LoginSessionService;
import top.aiolife.sso.wechat.WechatAuthService;
import top.aiolife.sso.wechat.WechatLoginVO;
import top.aiolife.sso.wechat.WechatMiniClient;
import top.aiolife.sso.wechat.WechatWebLoginService;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogParameters;
import top.aiolife.system.service.ActivityLogRecorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Controller、MVC 和日志切面；扫码兑换运行真实服务，仅替换存储和会话依赖。 */
class LoginAccessLogHttpTest {
    private final ActivityLogRecorder recorder = mock(ActivityLogRecorder.class);
    private final ActivityLogParameters parameters = mock(ActivityLogParameters.class);
    private final WechatAuthService mini = mock(WechatAuthService.class);
    private final WechatWebLoginService web = mock(WechatWebLoginService.class);
    private final QrLoginStore store = mock(QrLoginStore.class);
    private final UserMapper users = mock(UserMapper.class);
    private final LoginSessionService sessions = mock(LoginSessionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var qr = new QrLoginService(store, mock(AccountStatusGuard.class), users, sessions);
        mvc = MockMvcBuilders.standaloneSetup(
                        proxy(new WechatAuthController(mini, mock(WechatMiniClient.class))),
                        proxy(new WechatWebLoginController(web)), proxy(new QrLoginController(qr)))
                .setControllerAdvice(new ExceptionHandle()).build();
    }

    private Object proxy(Object controller) {
        var factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new ActivityLogAspect(recorder, parameters));
        return factory.getProxy();
    }

    private UserLoginVO login() {
        var login = new UserLoginVO();
        login.setId(13L);
        login.setAccessToken("web-token");
        return login;
    }

    private ActivityLogEntity recorded() {
        var rows = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(recorder).record(rows.capture(), any(), any());
        verifyNoInteractions(parameters);
        return rows.getValue();
    }

    @Test
    void 微信待注册后自动登录只记录实际登录() throws Exception {
        when(mini.login(anyString(), anyString())).thenReturn(
                WechatLoginVO.pending("REGISTRATION_REQUIRED", "private-ticket"));
        mvc.perform(post("/auth/wechat/mini/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginCode\":\"private-code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REGISTRATION_REQUIRED"));
        verifyNoInteractions(recorder);

        when(mini.register(any(), anyString())).thenReturn(WechatLoginVO.loggedIn(login(), true, "alice"));
        mvc.perform(post("/auth/wechat/mini/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginTicket\":\"private-ticket\",\"agreedPolicy\":true,\"policyVersion\":\"v1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("LOGGED_IN"));
        assertEquals("微信登录", recorded().getAccessType());
        assertTrue(recorded().getSuccess());
        assertEquals(13L, recorded().getUserId());
    }

    @Test
    void 微信扫码兑换仅记录一次且不把ModelAttribute当登录() throws Exception {
        when(web.exchange(anyString(), anyString(), anyString())).thenReturn(login());
        mvc.perform(post("/auth/wechat/web/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"" + "a".repeat(32) + "\",\"browserSecret\":\"" + "b".repeat(64) + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertEquals("微信扫码登录", recorded().getAccessType());
        assertTrue(recorded().getSuccess());
    }

    @Test
    void App扫码首次兑换记成功重取不重复记录() throws Exception {
        String id = "a".repeat(64), secret = "b".repeat(64);
        var ticket = new Ticket(SecureUtil.sha256(secret), "unused", "CONFIRMED", "test", "now", "1234",
                System.currentTimeMillis() + 120_000, 13L, "app-token", null);
        var confirmed = new QrLoginStore.Snapshot("confirmed", ticket);
        var issuing = new QrLoginStore.Snapshot("issuing", ticket.transition("ISSUING", 13L, "app-token", null));
        var login = login();
        var consumed = new QrLoginStore.Snapshot("consumed", ticket.transition("CONSUMED", 13L, "app-token", login));
        when(store.read(id)).thenReturn(confirmed, issuing, consumed);
        when(store.replace(eq(id), any(), any(), anyInt())).thenReturn(true);
        var user = new UserEntity(); user.setId(13L);
        when(users.selectById(13L)).thenReturn(user);
        when(sessions.completeQr(eq(user), anyString())).thenReturn(login);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken("app-token")).thenReturn(13L);
            stp.when(() -> StpUtil.getLoginIdByToken("web-token")).thenReturn(13L);
            stp.when(StpUtil::getStpLogic).thenReturn(mock(StpLogic.class));
            for (int attempt = 0; attempt < 2; attempt++) {
                mvc.perform(post("/auth/qr-login/consume").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"id\":\"" + id + "\",\"browserSecret\":\"" + secret + "\"}"))
                        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.accessToken").value("web-token"));
            }
        }
        verify(sessions).completeQr(eq(user), anyString());
        assertEquals("App 扫码登录", recorded().getAccessType());
        assertTrue(recorded().getSuccess());
    }
}
