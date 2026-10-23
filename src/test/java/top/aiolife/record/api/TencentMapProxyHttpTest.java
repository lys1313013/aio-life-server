package top.aiolife.record.api;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.aiolife.config.SaTokenConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.client.FootprintMapClient;
import top.aiolife.record.client.TencentMapProxyClient;
import top.aiolife.record.filter.TencentMapProxySessionFilter;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.system.service.FootprintTencentConfigService;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TencentMapProxyHttpTest {
    private final StpLogic previousLogic = StpUtil.getStpLogic();
    private final SaTokenContext previousContext = SaManager.getSaTokenContext();
    private final SaTokenDao previousDao = SaManager.getSaTokenDao();
    private SaTokenDaoDefaultImpl dao;
    private MockMvc mvc;
    private TencentMapProxyClient client;
    private SecondaryLockInterceptor locks;
    private AccountStatusGuard accounts;

    @BeforeEach void setup() throws Exception {
        dao = new SaTokenDaoDefaultImpl(); SaManager.setSaTokenDao(dao);
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        StpUtil.setStpLogic(new StpLogic("login").setConfig(new cn.dev33.satoken.config.SaTokenConfig()
                .setTokenName("Authorization").setTokenPrefix("Bearer").setIsReadCookie(false).setIsReadBody(false)));
        var config = mock(FootprintTencentConfigService.class);
        when(config.browserConfig()).thenReturn(new FootprintTencentConfigService.BrowserConfig(true, "style1"));
        var filter = new TencentMapProxySessionFilter(new NotificationCryptoService("synthetic-test-master"));
        client = mock(TencentMapProxyClient.class);
        when(client.forward(anyLong(), anyString(), any())).thenReturn(ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("application/javascript"))
                .body("TMap._svcb.test({\"info\":{\"error\":0}})".getBytes(StandardCharsets.UTF_8)));
        var apiKeys = mock(ApiKeyInterceptor.class); locks = mock(SecondaryLockInterceptor.class);
        var active = mock(UserLastActiveInterceptor.class); accounts = mock(AccountStatusGuard.class);
        when(apiKeys.preHandle(any(), any(), any())).thenReturn(true);
        when(locks.preHandle(any(), any(), any())).thenReturn(true);
        when(active.preHandle(any(), any(), any())).thenReturn(true);
        var registry = new Registry(); new SaTokenConfig(apiKeys, locks, active, accounts).addInterceptors(registry);
        mvc = MockMvcBuilders.standaloneSetup(new TencentMapProxyController(client),
                new FootprintMapController(mock(FootprintMapClient.class), config, filter))
                .addFilters(filter).addInterceptors(registry.interceptors()).setControllerAdvice(new ExceptionHandle()).build();
    }

    @AfterEach void cleanup() {
        StpUtil.setStpLogic(previousLogic); SaManager.setSaTokenContext(previousContext); SaManager.setSaTokenDao(previousDao);
        dao.destroy();
    }

    @Test void 登录配置不含Key且SDK用Cookie通过完整鉴权会话撤销后立即失效() throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(7L);
        var result = mvc.perform(get("/life-footprints/map/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tencent.enabled").value(true))
                .andExpect(jsonPath("$.data.tencent.key").doesNotExist())
                .andExpect(content().string(not(containsString("Ciphertext"))))
                .andReturn();
        var raw = result.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2);
        var cookie = new Cookie(raw[0], raw[1]);
        mvc.perform(get(TencentMapProxySessionFilter.PATH + "/checkKey").cookie(cookie).param("cb", "TMap._svcb.test"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("TMap._svcb.test")));
        verify(client).forward(eq(7L), eq("/checkKey"), any());
        verify(accounts, atLeast(2)).requireActive(7L); verify(locks, atLeast(2)).preHandle(any(), any(), any());
        StpUtil.logoutByTokenValue(token);
        mvc.perform(get(TencentMapProxySessionFilter.PATH + "/checkKey").cookie(cookie))
                .andExpect(jsonPath("$.code").value(not(0)));
        verify(client, times(1)).forward(anyLong(), anyString(), any());
    }

    @Test void 匿名伪造Cookie和POST不能访问上游且二级锁仍能拦截() throws Exception {
        mvc.perform(get(TencentMapProxySessionFilter.PATH + "/checkKey"))
                .andExpect(jsonPath("$.code").value(not(0)));
        mvc.perform(get(TencentMapProxySessionFilter.PATH).cookie(new Cookie("aio_tencent_proxy", "forged")))
                .andExpect(jsonPath("$.code").value(not(0)));
        String token = StpUtil.getStpLogic().createLoginSession(7L);
        mvc.perform(post(TencentMapProxySessionFilter.PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
        when(locks.preHandle(any(), any(), any())).thenReturn(false);
        mvc.perform(get(TencentMapProxySessionFilter.PATH).header("Authorization", "Bearer " + token));
        verifyNoInteractions(client);
    }

    private static class Registry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() { return getInterceptors().toArray(HandlerInterceptor[]::new); }
    }
}
