package top.aiolife.sso.wechat;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.aiolife.config.SaTokenConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.sso.api.WechatWebLoginController;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实 MVC 与 SaTokenConfig 的路由排除规则，只替换认证外部依赖。 */
class WechatWebLoginHttpTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    WechatWebLoginService service;
    MockedStatic<StpUtil> stp;
    MockedStatic<SaHolder> holder;
    SaStorage storage;
    static final String SCENE = "a".repeat(32);
    static final String SECRET = "b".repeat(64);
    static final String BROWSER = "{\"scene\":\"" + SCENE + "\",\"browserSecret\":\"" + SECRET + "\"}";

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        service = context.getBean(WechatWebLoginService.class);
        storage = mock(SaStorage.class);
        var request = mock(cn.dev33.satoken.context.model.SaRequest.class);
        when(request.getMethod()).thenReturn("POST");
        stp = mockStatic(StpUtil.class);
        holder = mockStatic(SaHolder.class);
        holder.when(SaHolder::getStorage).thenReturn(storage);
        holder.when(SaHolder::getRequest).thenReturn(request);
        stp.when(StpUtil::getLoginIdAsLong).thenThrow(new NotLoginException("未登录", "login", NotLoginException.NOT_TOKEN));
    }
    @AfterEach void cleanup() { holder.close(); stp.close(); context.close(); }

    @Test void 浏览器接口匿名可访问且敏感响应禁止缓存() throws Exception {
        when(service.create(anyString())).thenReturn(new WechatWebLoginService.Challenge(SCENE, SECRET, "data:image/png;base64,fixture", 300));
        when(service.status(SCENE, SECRET)).thenReturn("WAITING");
        mvc.perform(post("/auth/wechat/web/create")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
        for (String operation : new String[]{"status", "revoke", "exchange"}) {
            mvc.perform(post("/auth/wechat/web/" + operation).contentType(MediaType.APPLICATION_JSON).content(BROWSER))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        stp.verifyNoInteractions();
    }
    @Test void 扫码确认取消三个接口均不能匿名调用() throws Exception {
        for (String operation : new String[]{"scan", "confirm", "cancel"}) {
            mvc.perform(post("/auth/wechat/web/" + operation).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scene\":\"" + SCENE + "\",\"loginCode\":\"code\"}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
    @Test void APIKey身份不能确认扫码() throws Exception {
        when(storage.get("IS_API_KEY_AUTH")).thenReturn(true);
        mvc.perform(post("/auth/wechat/web/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scene\":\"" + SCENE + "\",\"loginCode\":\"code\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void 当前会话身份传给确认服务不接受客户端用户ID() throws Exception {
        stp.when(StpUtil::getLoginIdAsLong).thenReturn(13L);
        when(service.confirm(eq(SCENE), eq(13L), eq("fresh-code"), anyString())).thenReturn("CONFIRMED");
        mvc.perform(post("/auth/wechat/web/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scene\":\"" + SCENE + "\",\"loginCode\":\"fresh-code\",\"userId\":\"99\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        verify(service).confirm(eq(SCENE), eq(13L), eq("fresh-code"), anyString());
    }
    @Test void 缺失浏览器密钥在业务处理前拒绝() throws Exception {
        mvc.perform(post("/auth/wechat/web/exchange").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scene\":\"" + SCENE + "\"}"))
                .andExpect(jsonPath("$.code").value(100400));
        verifyNoInteractions(service);
    }

    @Configuration @EnableWebMvc
    static class Config {
        @Bean WechatWebLoginService service() { return mock(WechatWebLoginService.class); }
        @Bean WechatWebLoginController controller(WechatWebLoginService service) { return new WechatWebLoginController(service); }
        @Bean ExceptionHandle errors() { return new ExceptionHandle(); }
        @Bean SaTokenConfig auth() throws Exception {
            var api = mock(ApiKeyInterceptor.class);
            var locks = mock(SecondaryLockInterceptor.class);
            var activity = mock(UserLastActiveInterceptor.class);
            when(api.preHandle(any(), any(), any())).thenReturn(true);
            when(locks.preHandle(any(), any(), any())).thenReturn(true);
            when(activity.preHandle(any(), any(), any())).thenReturn(true);
            return new SaTokenConfig(api, locks, activity, mock(AccountStatusGuard.class));
        }
    }
}
