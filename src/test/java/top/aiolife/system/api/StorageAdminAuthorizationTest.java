package top.aiolife.system.api;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.system.pojo.vo.StoragePageVO;
import top.aiolife.system.service.StorageAdminService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Sa-Token 登录会话、角色注解与 MVC 拦截器；仅存储服务及角色数据源模拟。 */
class StorageAdminAuthorizationTest {
    private MockMvc mvc;
    private StorageAdminService service;
    private StpInterface roles;
    private final StpLogic previousLogic = StpUtil.getStpLogic();
    private final PreviousState previous = new PreviousState();

    private static class PreviousState {
        final SaTokenContext context = SaManager.getSaTokenContext();
        final SaTokenDao dao = SaManager.getSaTokenDao();
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
        service = mock(StorageAdminService.class);
        when(service.list(any())).thenReturn(new StoragePageVO("business", "", List.of(), null));
        mvc = MockMvcBuilders.standaloneSetup(new StorageAdminController(service))
                .setControllerAdvice(new StorageAdminExceptionHandler(), new ExceptionHandle())
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
    void 所有端点拒绝未登录且不访问存储() throws Exception {
        for (String endpoint : List.of("objects", "preview", "download")) {
            mvc.perform(get("/system/storage/" + endpoint).param("key", "private.jpg"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void 普通用户直接请求所有端点均被拒绝() throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(2L);
        for (String endpoint : List.of("objects", "preview", "download")) {
            mvc.perform(get("/system/storage/" + endpoint).param("key", "private.jpg")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void 管理员可访问但角色撤销后原Token无法继续访问() throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(get("/system/storage/objects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"))
                .andExpect(header().string("Cache-Control", "no-store"));
        for (String endpoint : List.of("preview", "download")) {
            mvc.perform(get("/system/storage/" + endpoint).param("key", "中文 +#.jpg")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
        verify(service).read(eq("中文 +#.jpg"), eq(false), any());
        verify(service).read(eq("中文 +#.jpg"), eq(true), any());
        clearInvocations(service);
        doReturn(List.of("user")).when(roles).getRoleList(any(), any());
        mvc.perform(get("/system/storage/preview").param("key", "private.jpg")
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 存储异常返回HTTP错误而不是伪装成文件() throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(1L);
        doThrow(new IOException("fixture connection failure")).when(service).read(anyString(), anyBoolean(), any());
        mvc.perform(get("/system/storage/preview").param("key", "private.jpg")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.rscode").value("502"));
    }

    @Test
    void 删除接口仅管理员可用且透传关联冲突() throws Exception {
        mvc.perform(delete("/system/storage/object").param("key", "orphan.jpg"))
                .andExpect(status().isUnauthorized());
        String user = StpUtil.getStpLogic().createLoginSession(2L);
        mvc.perform(delete("/system/storage/object").param("key", "orphan.jpg")
                        .header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        String admin = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(delete("/system/storage/object").param("key", "中文 +#.jpg")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"));
        verify(service).delete("中文 +#.jpg");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "file 表存在关联记录")).when(service).delete("linked.jpg");
        mvc.perform(delete("/system/storage/object").param("key", "linked.jpg")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.result").value("file 表存在关联记录"));
    }

    @Test
    void 拒绝无界分页参数() throws Exception {
        String token = StpUtil.getStpLogic().createLoginSession(1L);
        mvc.perform(get("/system/storage/objects").param("pageSize", "1000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.rscode").value("100400"));
        verifyNoInteractions(service);
    }
}
