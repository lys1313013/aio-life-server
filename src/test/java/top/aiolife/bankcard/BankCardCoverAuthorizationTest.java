package top.aiolife.bankcard;
import top.aiolife.bankcard.api.BankCardCoverTemplateAdminController;
import top.aiolife.bankcard.service.BankCardService;
import top.aiolife.bankcard.service.BankCardCoverTemplateService;
import top.aiolife.record.service.IFileService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.core.exception.ExceptionHandle;



import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Sa-Token 登录会话、角色注解与 MVC 拦截器；仅存储服务及角色数据源模拟。 */
class BankCardCoverAuthorizationTest {
    private MockMvc mvc;
    private BankCardCoverTemplateService service;
    private StpInterface roles;
    private IFileService files;
    private final StpLogic previousLogic = StpUtil.getStpLogic();
    private final PreviousState previous = new PreviousState();

    private static class PreviousState {
        final cn.dev33.satoken.context.SaTokenContext context = SaManager.getSaTokenContext();
        final cn.dev33.satoken.dao.SaTokenDao dao = SaManager.getSaTokenDao();
        final StpInterface roles = SaManager.getStpInterface();
    }

    @BeforeEach
    void setup() throws Exception {
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        roles = mock(StpInterface.class);
        when(roles.getRoleList(any(), any())).thenAnswer(call ->
                List.of("1".equals(call.getArgument(0).toString()) ? "admin" : "user"));
        SaManager.setStpInterface(roles);
        StpUtil.setStpLogic(new StpLogic("login").setConfig(new SaTokenConfig()
                .setTokenName("Authorization").setTokenPrefix("Bearer").setIsReadCookie(false)));
        service = mock(BankCardCoverTemplateService.class);
        when(service.list()).thenReturn(List.of());
        files=mock(IFileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BankCardCoverTemplateAdminController(service,mock(BankCardService.class),files))
                .setControllerAdvice(new ExceptionHandle())
                .addInterceptors(new SaInterceptor(handler -> StpUtil.checkLogin())).build();
    }

    @AfterEach
    void restore() {
        StpUtil.setStpLogic(previousLogic);
        SaManager.setSaTokenContext(previous.context);
        SaManager.setSaTokenDao(previous.dao);
        SaManager.setStpInterface(previous.roles);
    }

    @Test
    void 管理端全部操作拒绝普通用户且不调用服务() throws Exception {
        String token=StpUtil.getStpLogic().createLoginSession(2L);
        for (var req : List.of(get("/system/bank-card-covers"),get("/system/bank-card-covers/banks"),
                post("/system/bank-card-covers").contentType("application/json").content("{}"),
                put("/system/bank-card-covers/1").contentType("application/json").content("{}"),
                put("/system/bank-card-covers/1/enabled").contentType("application/json").content("{\"isEnabled\":0}"),
                delete("/system/bank-card-covers/1"),
                multipart("/system/bank-card-covers/upload").file("file",new byte[]{1}))) {
            mvc.perform(req.header("Authorization","Bearer "+token))
                    .andExpect(jsonPath("$.rscode").value(org.hamcrest.Matchers.not("0")));
        }
        verifyNoInteractions(service,files);
    }
    @Test
    void 管理员可读但匿名不可读() throws Exception {
        mvc.perform(get("/system/bank-card-covers")).andExpect(status().isUnauthorized());
        String token=StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(get("/system/bank-card-covers").header("Authorization","Bearer "+token))
                .andExpect(jsonPath("$.rscode").value("0"));
        verify(service).list();
    }
}
