package top.aiolife.sso.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import jakarta.servlet.http.Cookie;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.aiolife.bankcard.mapper.BankCardFileMapper;
import top.aiolife.config.SaTokenConfig;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IMailService;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.mapper.*;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.service.impl.ApiKeyServiceImpl;
import top.aiolife.sso.service.impl.UserServiceImpl;
import top.aiolife.sso.util.PasswordUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 Mapper、事务、Sa-Token 和生产拦截器；H2 与内存会话隔离外部服务。 */
class CredentialRevocationTest {
    private final StpLogic previousLogic = StpUtil.getStpLogic();
    private final cn.dev33.satoken.context.SaTokenContext previousContext = SaManager.getSaTokenContext();
    private final cn.dev33.satoken.dao.SaTokenDao previousDao = SaManager.getSaTokenDao();
    private SaTokenDaoDefaultImpl sessionDao;
    private JdbcTemplate jdbc;
    private UserServiceImpl users;
    private UserMapper userMapper;
    private RedisUtil redis;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:revoke_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE `user` (
                    id BIGINT PRIMARY KEY, username VARCHAR(255), password VARCHAR(255), password_salt VARCHAR(255),
                    secondary_password VARCHAR(255), secondary_password_salt VARCHAR(255), nickname VARCHAR(255),
                    avatar VARCHAR(255), email VARCHAR(255), phone_country_code VARCHAR(32), phone VARCHAR(64),
                    phone_verified_at TIMESTAMP, wechat_openid VARCHAR(255), wechat_unionid VARCHAR(255),
                    role VARCHAR(255), introduction VARCHAR(255), is_deleted INT DEFAULT 0, last_active_at TIMESTAMP,
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE api_key (
                    id BIGINT PRIMARY KEY, user_id BIGINT, api_key VARCHAR(255), remark VARCHAR(255),
                    expired_at TIMESTAMP, create_user BIGINT, update_user BIGINT, create_time TIMESTAMP,
                    update_time TIMESTAMP, is_deleted INT DEFAULT 0)
                """);
        jdbc.update("INSERT INTO `user` (id, email, password, password_salt) VALUES (1, 'one@example.com', ?, 'salt'), (2, 'two@example.com', ?, 'salt')",
                PasswordUtil.encryptPassword("old-password", "salt"), PasswordUtil.encryptPassword("old-password", "salt"));
        jdbc.update("INSERT INTO api_key (id, user_id, api_key) VALUES (11, 1, 'ak-one'), (12, 1, 'ak-one-extra'), (21, 2, 'ak-two')");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(ApiKeyMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        userMapper = session.getMapper(UserMapper.class);
        var apiKeyMapper = session.getMapper(ApiKeyMapper.class);
        redis = mock(RedisUtil.class);
        var implementation = new UserServiceImpl(userMapper, apiKeyMapper, mock(LoginSessionService.class),
                mock(LoginLogMapper.class), mock(IMailService.class), redis,
                mock(UserSecondaryLockMenuMapper.class), mock(SecondaryLockMenuCache.class));
        var proxy = new ProxyFactory(implementation);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        users = (UserServiceImpl) proxy.getProxy();

        sessionDao = new SaTokenDaoDefaultImpl();
        SaManager.setSaTokenDao(sessionDao);
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        StpUtil.setStpLogic(new StpLogic("login").setConfig(new cn.dev33.satoken.config.SaTokenConfig()
                .setTokenName("Authorization").setTokenPrefix("Bearer").setIsShare(false).setIsConcurrent(true)));
        var accountGuard = new AccountStatusGuard(userMapper);
        var keys = new ApiKeyServiceImpl();
        ReflectionTestUtils.setField(keys, "baseMapper", apiKeyMapper);
        var apiKeyInterceptor = new ApiKeyInterceptor(keys, mock(IApiKeyLogService.class), accountGuard);
        var locks = mock(SecondaryLockInterceptor.class);
        var lastActive = mock(UserLastActiveInterceptor.class);
        when(locks.preHandle(any(), any(), any())).thenReturn(true);
        when(lastActive.preHandle(any(), any(), any())).thenReturn(true);
        var registry = new TestRegistry();
        new SaTokenConfig(apiKeyInterceptor, locks, lastActive, accountGuard).addInterceptors(registry);
        var files = new FilePreviewGuard(mock(SecondaryLockGuard.class), mock(BankCardFileMapper.class), accountGuard, mock(top.aiolife.record.mapper.IBVideoMapper.class));
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController(files))
                .setControllerAdvice(new ExceptionHandle()).addInterceptors(registry.interceptors()).build();
    }

    @AfterEach
    void cleanup() {
        StpUtil.setStpLogic(previousLogic);
        SaManager.setSaTokenContext(previousContext);
        SaManager.setSaTokenDao(previousDao);
        if (sessionDao != null) sessionDao.destroy();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void 删除账号撤销全部设备和密钥且不影响其他账号() throws Exception {
        String web = token(1, "web"), mobile = token(1, "mobile"), other = token(2, "web");
        assertAccess(web, 1);
        assertFileIdentity(web, "1");
        assertAccess("ak-one", 1);
        assertAccess("ak-one-extra", 1);
        users.deleteUser(1L);
        assertNull(StpUtil.getLoginIdByToken(web));
        assertNull(StpUtil.getLoginIdByToken(mobile));
        assertRejected(web);
        assertRejected(mobile);
        assertRejected("ak-one");
        assertRejected("ak-one-extra");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM api_key WHERE user_id=1 AND is_deleted=1", Integer.class));
        assertAccess(other, 2);
        assertAccess("ak-two", 2);
        assertFileRejected(web);
    }

    @Test
    void 密码找回撤销全部旧会话并允许新会话() throws Exception {
        String web = token(1, "web"), mobile = token(1, "mobile"), other = token(2, "web");
        when(redis.get("reset:code:one@example.com")).thenReturn("123456");
        users.resetPassword(resetRequest("123456"));
        assertRejected(web);
        assertRejected(mobile);
        assertNull(StpUtil.getLoginIdByToken(web));
        assertNull(StpUtil.getLoginIdByToken(mobile));
        assertFileRejected(web);
        var user = userMapper.selectById(1L);
        assertEquals(PasswordUtil.encryptPassword("new-password", user.getPasswordSalt()), user.getPassword());
        assertAccess(token(1, "web"), 1);
        assertAccess(other, 2);
        verify(redis).delete("reset:code:one@example.com");
    }

    @Test
    void 验证码错误不更改密码或撤销会话() throws Exception {
        String web = token(1, "web");
        String password = userMapper.selectById(1L).getPassword();
        when(redis.get("reset:code:one@example.com")).thenReturn("123456");
        assertThrows(RuntimeException.class, () -> users.resetPassword(resetRequest("wrong")));
        assertEquals(password, userMapper.selectById(1L).getPassword());
        assertAccess(web, 1);
    }

    @Test
    void 已删除账号的历史遗留会话和密钥也被认证入口拒绝() throws Exception {
        String web = token(1, "web");
        assertAccess(web, 1);
        jdbc.update("UPDATE `user` SET is_deleted=1 WHERE id=1");
        assertNotNull(StpUtil.getLoginIdByToken(web));
        assertRejected(web);
        assertRejected("ak-one");
        assertFileRejected(web);
        assertAccess("ak-two", 2);
        // 重复删除仍可清理修复前遗留的凭据。
        users.deleteUser(1L);
        assertNull(StpUtil.getLoginIdByToken(web));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM api_key WHERE user_id=1 AND is_deleted=0", Integer.class));
    }

    @Test
    void 撤销会话失败回滚账号和密钥删除() {
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.logout(1L)).thenThrow(new IllegalStateException("session store unavailable"));
            assertThrows(IllegalStateException.class, () -> users.deleteUser(1L));
        }
        assertNotNull(userMapper.selectById(1L));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM api_key WHERE user_id=1 AND is_deleted=0", Integer.class));
    }

    @Test
    void 撤销会话失败不提交新密码() {
        String password = userMapper.selectById(1L).getPassword();
        when(redis.get("reset:code:one@example.com")).thenReturn("123456");
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.logout(1L)).thenThrow(new IllegalStateException("session store unavailable"));
            assertThrows(IllegalStateException.class, () -> users.resetPassword(resetRequest("123456")));
        }
        assertEquals(password, userMapper.selectById(1L).getPassword());
    }

    private String token(long id, String device) {
        return StpUtil.getStpLogic().createLoginSession(id, new SaLoginModel().setDevice(device));
    }

    private ResetPasswordReq resetRequest(String code) {
        var request = new ResetPasswordReq();
        request.setEmail("one@example.com");
        request.setPassword("new-password");
        request.setCode(code);
        return request;
    }

    private void assertAccess(String credential, long id) throws Exception {
        mvc.perform(get("/credential-probe").header("Authorization", "Bearer " + credential))
                .andExpect(status().isOk()).andExpect(content().string(Long.toString(id)));
    }

    private void assertRejected(String credential) throws Exception {
        mvc.perform(get("/credential-probe").header("Authorization", "Bearer " + credential))
                .andExpect(status().isUnauthorized());
    }

    private void assertFileRejected(String token) throws Exception {
        assertFileIdentity(token, "anonymous");
    }

    private void assertFileIdentity(String token, String expected) throws Exception {
        mvc.perform(get("/file/preview/probe").param("Authorization", token))
                .andExpect(status().isOk()).andExpect(content().string(expected));
        mvc.perform(get("/file/preview/probe").cookie(new Cookie("Authorization", "Bearer " + token)))
                .andExpect(status().isOk()).andExpect(content().string(expected));
        mvc.perform(get("/file/preview/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string(expected));
    }

    private static class TestRegistry extends InterceptorRegistry {
        HandlerInterceptor[] interceptors() {
            return getInterceptors().toArray(HandlerInterceptor[]::new);
        }
    }

    @RestController
    private static class ProbeController {
        private final FilePreviewGuard files;
        ProbeController(FilePreviewGuard files) { this.files = files; }

        @GetMapping("/credential-probe")
        String user() { return Long.toString(StpUtil.getLoginIdAsLong()); }

        @GetMapping("/file/preview/probe")
        String fileUser() {
            Long id = files.resolveLoginUserId();
            return id == null ? "anonymous" : id.toString();
        }
    }
}
