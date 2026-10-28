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
import top.aiolife.scheduling.api.ScheduledJobController;
import top.aiolife.scheduling.service.ScheduledJobService;
import top.aiolife.scheduling.service.ScheduledJobRuntime;
import top.aiolife.scheduling.pojo.vo.ScheduledJobVO;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用生产 SaTokenConfig 路由规则、真实登录会话和角色校验。 */
class ScheduledJobHttpTest {
    private MockMvc mvc;
    private ScheduledJobService service;
    private ScheduledJobRuntime runtime;
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
        service = mock(ScheduledJobService.class);
        runtime = mock(ScheduledJobRuntime.class);
        var mapper = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        mvc = MockMvcBuilders.standaloneSetup(new ScheduledJobController(service, runtime))
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

    @Test void 所有接口拒绝匿名和普通用户() throws Exception {
        String user = StpUtil.getStpLogic().createLoginSession(2L);
        for (String token : List.of("", "Bearer " + user)) {
            mvc.perform(get("/system/scheduled-jobs").header("Authorization", token)).andExpect(jsonPath("$.code").value(not(0)));
            mvc.perform(get("/system/scheduled-jobs/1/logs").header("Authorization", token)).andExpect(jsonPath("$.code").value(not(0)));
            mvc.perform(post("/system/scheduled-jobs/1/run").header("Authorization", token)).andExpect(jsonPath("$.code").value(not(0)));
            mvc.perform(put("/system/scheduled-jobs/1").header("Authorization", token).contentType("application/json")
                    .content("{\"cron\":\"0 0 7 * * ?\",\"isEnabled\":1,\"revision\":0}")).andExpect(jsonPath("$.code").value(not(0)));
        }
        verifyNoInteractions(service, runtime);
    }

    @Test void 管理员可查看大ID并且修改入参必须完整() throws Exception {
        String admin = StpUtil.getStpLogic().createLoginSession(1L);
        var row = new ScheduledJobVO(); row.setId(9223372036854775806L); row.setRevision(0);
        when(service.list()).thenReturn(List.of(row));
        mvc.perform(get("/system/scheduled-jobs").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data[0].id").value("9223372036854775806"))
                .andExpect(jsonPath("$.data[0].revision").value(0));
        mvc.perform(put("/system/scheduled-jobs/1").header("Authorization", "Bearer " + admin).contentType("application/json")
                .content("{\"cron\":\"0 0 7 * * ?\"}")).andExpect(jsonPath("$.code").value(not(0)));
        verify(service, never()).update(any(), any(), any());
        mvc.perform(post("/system/scheduled-jobs/9223372036854775806/run").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(0));
        verify(runtime).runNow(9223372036854775806L, 1L);
    }
}
