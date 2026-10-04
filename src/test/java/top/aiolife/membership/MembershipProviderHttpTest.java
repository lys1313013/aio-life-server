package top.aiolife.membership;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.membership.api.MembershipProviderController;
import top.aiolife.membership.api.MembershipProviderAdminController;
import top.aiolife.membership.service.MembershipIconCatalog;
import top.aiolife.membership.service.MembershipProviderService;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用生产 SaTokenConfig 路由规则、真实登录会话和角色校验。 */
class MembershipProviderHttpTest {
    private MockMvc mvc;
    private MembershipProviderService service;
    private MembershipIconCatalog icons;
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
        service = mock(MembershipProviderService.class);
        when(service.list(anyBoolean())).thenReturn(List.of());
        icons = new MembershipIconCatalog(new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new MembershipProviderController(service, icons), new MembershipProviderAdminController(service))
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

    @Test void 每个内置图标匿名可读且不经过账户和个人二级锁() throws Exception {
        for (var icon : icons.list()) {
            mvc.perform(get("/membership/provider-icons/" + icon.key()))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andExpect(content().bytes(icons.resource(icon.key()).getContentAsByteArray()))
                    .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        verifyNoInteractions(keys, locks, activity, accounts, service);
    }

    @Test void 未注册图标返回不可缓存404且文件名不能作为图标key() throws Exception {
        for (String key : List.of("unknown", "membership-icons.json", "bilibili.png", "application.yml")) {
            mvc.perform(get("/membership/provider-icons/" + key)).andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(service);
    }

    @Test void 平台和图标清单必须登录管理接口普通用户无权访问() throws Exception {
        for (String path : List.of("/membership/providers", "/membership/provider-icons", "/system/membership-providers")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        String user = StpUtil.getStpLogic().createLoginSession(2L);
        mvc.perform(get("/membership/providers").header("Authorization", "Bearer " + user)).andExpect(jsonPath("$.rscode").value("0"));
        mvc.perform(get("/membership/provider-icons").header("Authorization", "Bearer " + user)).andExpect(jsonPath("$.data[0].key").exists());
        clearInvocations(service);
        mvc.perform(get("/system/membership-providers").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.rscode").value(not("0")));
        mvc.perform(delete("/system/membership-providers/10").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.rscode").value(not("0")));
        verifyNoInteractions(service);
        String admin = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(get("/system/membership-providers").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.rscode").value("0"));
        verify(service).list(false);
    }

    @Test void 管理员保存执行字段校验且无上传入口() throws Exception {
        String admin = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(post("/system/membership-providers").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"平台\",\"code\":\"../bad\",\"category\":\"invalid\",\"isEnabled\":2}"))
                .andExpect(jsonPath("$.rscode").value(not("0")));
        verifyNoInteractions(service);
    }
}
