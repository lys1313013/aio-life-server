package top.aiolife.sso.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
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
    @InjectMocks UserServiceImpl service;

    @Test void 微信无密码账号不能密码登录且不会空指针() {
        when(users.selectOne(any())).thenReturn(new UserEntity());
        LoginReq request = new LoginReq(); request.setUsername("u_test"); request.setPassword("anything");
        var error = assertThrows(RuntimeException.class, () -> service.login(request, "ip"));
        assertEquals("用户名或密码错误", error.getMessage());
        verifyNoInteractions(sessions);
    }

    @Test void 原账号密码验证通过后使用公共会话签发() {
        UserEntity user = new UserEntity(); user.setPasswordSalt("salt");
        user.setPassword(PasswordUtil.encryptPassword("password", "salt"));
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
}
