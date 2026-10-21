package top.aiolife.system.api;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogService;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用生产 SaTokenConfig 路由规则、真实登录会话和角色校验。 */
class ActivityLogHttpTest {
    private MockMvc mvc;
    private ActivityLogService service;
    private ApiKeyInterceptor keys;
    private SecondaryLockInterceptor locks;
    private UserLastActiveInterceptor activity;
    private AccountStatusGuard accounts;
    private final StpLogic previousLogic = StpUtil.getStpLogic();
    private final SaTokenContext previousContext = SaManager.getSaTokenContext();
    private final SaTokenDao previousDao = SaManager.getSaTokenDao();
    private final StpInterface previousRoles = SaManager.getStpInterface();

    @BeforeEach void setup() throws Exception {
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        StpInterface roles = mock(StpInterface.class);
        when(roles.getRoleList(any(), any())).thenAnswer(call -> List.of("1".equals(call.getArgument(0).toString()) ? "admin" : "user"));
        SaManager.setStpInterface(roles);
        StpUtil.setStpLogic(new StpLogic("login").setConfig(new SaTokenConfig()
                .setTokenName("Authorization").setTokenPrefix("Bearer").setIsReadCookie(false)));
        keys = mock(ApiKeyInterceptor.class);
        locks = mock(SecondaryLockInterceptor.class);
        activity = mock(UserLastActiveInterceptor.class);
        accounts = mock(AccountStatusGuard.class);
        when(keys.preHandle(any(), any(), any())).thenReturn(true);
        when(locks.preHandle(any(), any(), any())).thenReturn(true);
        when(activity.preHandle(any(), any(), any())).thenReturn(true);
        var registry = new TestRegistry();
        new top.aiolife.config.SaTokenConfig(keys, locks, activity, accounts).addInterceptors(registry);
        service = mock(ActivityLogService.class);
        var mapper = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        mvc = MockMvcBuilders.standaloneSetup(new ActivityLogController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new ExceptionHandle()).addInterceptors(registry.interceptors()).build();
    }

    @AfterEach void restore() {
        StpUtil.setStpLogic(previousLogic);
        SaManager.setSaTokenContext(previousContext);
        SaManager.setSaTokenDao(previousDao);
        SaManager.setStpInterface(previousRoles);
    }

    private static class TestRegistry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() { return getInterceptors().toArray(HandlerInterceptor[]::new); }
    }

    @Test void 列表详情导出均要求管理员() throws Exception {
        var paths = List.of("/system/logs/operation", "/system/logs/operation/9223372036854775806", "/system/logs/operation/export");
        String user = StpUtil.getStpLogic().createLoginSession(2L);
        for (String path : paths) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(jsonPath("$.code").value(not(0)));
        }
        verifyNoInteractions(service);
    }

    @Test void 详情保留大ID毫秒零耗时与JSON对象且新字段明确为null() throws Exception {
        var row = new ActivityLogEntity(); row.setId(9223372036854775806L); row.setUserId(9223372036854775805L);
        row.setDurationMs(0); row.setRequestTime(LocalDateTime.of(2026,10,21,12,0,0,123000000));
        row.setRequestParams("{\"path\":{\"id\":\"9223372036854775806\"}}");
        when(service.detail(9223372036854775806L)).thenReturn(row);
        String admin = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(get("/system/logs/operation/9223372036854775806").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("9223372036854775806"))
                .andExpect(jsonPath("$.data.userId").value("9223372036854775805"))
                .andExpect(jsonPath("$.data.durationMs").value(0))
                .andExpect(jsonPath("$.data.requestTime").value("2026-10-21 12:00:00.123"))
                .andExpect(jsonPath("$.data.requestParams.path.id").value("9223372036854775806"))
                .andExpect(jsonPath("$.data.serverIp").value(nullValue()));
        row.setRequestParams(null);
        mvc.perform(get("/system/logs/operation/9223372036854775806").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.requestParams").value(nullValue()));
        when(service.list(eq("OPERATION"), any())).thenReturn(PageResp.of(List.of(row), 1));
        mvc.perform(get("/system/logs/operation").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.items[0].requestParams").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
