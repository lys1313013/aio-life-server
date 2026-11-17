package top.aiolife.sso.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.entity.LoginLogEntity;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.service.LegalPolicyService;
import top.aiolife.sso.service.LoginSessionService;
import top.aiolife.sso.service.impl.UserServiceImpl;
import top.aiolife.sso.util.PasswordUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordLoginCompatibilityTest {
    @Mock UserMapper users;
    @Mock LoginLogMapper logs;
    @Mock LoginSessionService sessions;
    @Mock RedisUtil redis;
    @InjectMocks UserServiceImpl service;

    @Test void 微信无密码账号不能密码登录且不会空指针() {
        when(users.selectOne(any())).thenReturn(new UserEntity());
        LoginReq request = new LoginReq(); request.setUsername("u_test"); request.setPassword("anything");
        var error = assertThrows(RuntimeException.class, () -> service.login(request, "ip"));
        assertEquals("用户名或密码错误", error.getMessage());
        verifyNoInteractions(sessions);
    }

    @Test void 原账号密码验证通过后使用公共会话签发() {
        UserEntity user = new UserEntity();
        user.setPassword(PasswordUtil.hashPassword("password"));
        when(users.selectOne(any())).thenReturn(user);
        LoginReq request = new LoginReq(); request.setUsername("old"); request.setPassword("password");
        service.login(request, "ip");
        verify(sessions).complete(user, "ip", true);
    }

    @Test void 无密码账号修改密码返回明确业务错误() {
        when(users.selectById(1L)).thenReturn(new UserEntity());
        var error = assertThrows(RuntimeException.class, () -> service.changePassword(1L, new ChangePasswordReq()));
        assertTrue(error.getMessage().contains("尚未设置密码"));
    }

    @Test void 修改密码验证旧bcrypt并写入新bcrypt() {
        UserEntity user = new UserEntity();
        user.setPassword(PasswordUtil.hashPassword("old-password"));
        when(users.selectById(1L)).thenReturn(user);
        var req = new ChangePasswordReq();
        req.setOldPassword("wrong");
        req.setNewPassword("new-password");
        assertThrows(RuntimeException.class, () -> service.changePassword(1L, req));
        verify(users, never()).updateById(any(UserEntity.class));
        req.setOldPassword("old-password");
        service.changePassword(1L, req);
        assertTrue(PasswordUtil.matches("new-password", user.getPassword()));
        assertFalse(PasswordUtil.matches("old-password", user.getPassword()));
        verify(users).updateById(user);
    }

    @Test void 注册及管理员创建账号都只保存bcrypt() {
        var req = new RegisterReq();
        req.setUsername("new-user");
        req.setEmail("bcrypt@example.com");
        req.setPassword("register-password");
        req.setCode("123456");
        req.setAgreedPolicy(true);
        req.setPolicyVersion(LegalPolicyService.VERSION);
        when(redis.get("register:code:bcrypt@example.com")).thenReturn("123456");
        service.register(req);
        var created = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).insert(created.capture());
        assertTrue(PasswordUtil.matches("register-password", created.getValue().getPassword()));

        var adminCreated = new UserEntity();
        adminCreated.setPassword("admin-created-password");
        service.addUser(adminCreated, null);
        assertTrue(PasswordUtil.matches("admin-created-password", adminCreated.getPassword()));
        verify(users).insert(adminCreated);
    }

    @Test void 超长新密码不会消耗注册或重置验证码() {
        var register = new RegisterReq();
        register.setPassword("a".repeat(73));
        var reset = new ResetPasswordReq();
        reset.setPassword("中".repeat(25));
        assertThrows(IllegalArgumentException.class, () -> service.register(register));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(reset));
        verifyNoInteractions(redis, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "noPassword", "wrongPassword"})
    void 失败登录保留审计但不保存用户输入密码(String scenario) {
        UserEntity user = null;
        if (!scenario.equals("missing")) {
            user = new UserEntity();
            if (scenario.equals("wrongPassword")) {
                user.setPassword(PasswordUtil.hashPassword("actual-password"));
            }
        }
        when(users.selectOne(any())).thenReturn(user);
        var request = new LoginReq();
        request.setUsername("u_test");
        request.setPassword("test-only-secret");
        assertThrows(RuntimeException.class, () -> service.login(request, "127.0.0.1"));
        var audit = ArgumentCaptor.forClass(LoginLogEntity.class);
        verify(logs).insert(audit.capture());
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(audit.getValue()).has("password"));
        assertEquals("u_test", audit.getValue().getUsername());
        assertEquals("127.0.0.1", audit.getValue().getIpAddress());
        verifyNoInteractions(sessions);
    }
}
