package top.aiolife.sso.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.record.service.IMailService;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.mapper.ApiKeyMapper;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.impl.UserServiceImpl;
import top.aiolife.sso.util.PasswordUtil;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecondaryPasswordTest {
    private UserMapper mapper;
    private RedisUtil redis;
    private UserServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setup() {
        mapper = mock(UserMapper.class);
        redis = mock(RedisUtil.class);
        var menus = mock(SecondaryLockMenuResolver.class);
        when(menus.findMatchedPaths(1L, "/finance")).thenReturn(Set.of("/finance"));
        service = new UserServiceImpl(mapper, mock(ApiKeyMapper.class), mock(LoginSessionService.class),
                mock(LoginLogMapper.class), mock(IMailService.class), redis,
                mock(UserSecondaryLockMenuMapper.class), menus, mock(UserAvatarFileService.class));
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("fixture@example.com");
        when(mapper.selectById(1L)).thenReturn(user);
    }

    @Test
    void 设置和重置拒绝少于四位非数字和空值且不消耗验证码() {
        for (String invalid : new String[]{null, "", "123", "1234a", " 1234", "１２３４", "1234\n"}) {
            assertThrows(IllegalArgumentException.class, () -> service.setSecondaryPassword(1L, invalid, null));
            assertThrows(IllegalArgumentException.class, () -> service.resetSecondaryPassword(1L, "123456", invalid));
        }
        verify(mapper, never()).updateById(any(UserEntity.class));
        verifyNoInteractions(redis);
    }

    @Test
    void 数字密码保留前导零且不限制固定长度() {
        for (String value : new String[]{"0012", "0".repeat(300)}) {
            user.setSecondaryPassword(null);
            service.setSecondaryPassword(1L, value, null);
            assertEquals(PasswordUtil.encryptPassword(value, user.getSecondaryPasswordSalt()), user.getSecondaryPassword());
            assertDoesNotThrow(() -> service.verifySecondaryPassword(1L, value, "/finance"));
        }
    }

    @Test
    void 字母旧密码不能解锁修改或保存菜单锁() {
        user.setSecondaryPasswordSalt("fixture-salt");
        user.setSecondaryPassword(PasswordUtil.encryptPassword("legacy-password", "fixture-salt"));
        for (String invalid : new String[]{"legacy-password", "123", "１２３４", "1234a"}) {
            assertThrows(IllegalArgumentException.class, () -> service.verifySecondaryPassword(1L, invalid, "/finance"));
            assertThrows(IllegalArgumentException.class, () -> service.setSecondaryPassword(1L, "0012", invalid));
            assertThrows(IllegalArgumentException.class, () -> service.saveSecondaryLockMenus(1L, List.of(), invalid));
        }
        verify(mapper, never()).updateById(any(UserEntity.class));
        verifyNoInteractions(redis);
    }

    @Test
    void 修改数字密码仍受失败次数限制() {
        user.setSecondaryPasswordSalt("fixture-salt");
        user.setSecondaryPassword(PasswordUtil.encryptPassword("0011", "fixture-salt"));
        assertThrows(RuntimeException.class, () -> service.setSecondaryPassword(1L, "0012", "9999"));
        verify(mapper, never()).updateById(any(UserEntity.class));
        when(redis.get(SecondaryLockInterceptor.failCountKey(1L))).thenReturn("5");
        assertThrows(RuntimeException.class, () -> service.setSecondaryPassword(1L, "0012", "0011"));
        when(redis.get(SecondaryLockInterceptor.failCountKey(1L))).thenReturn(null);
        service.setSecondaryPassword(1L, "0012", "0011");
        assertEquals(PasswordUtil.encryptPassword("0012", user.getSecondaryPasswordSalt()), user.getSecondaryPassword());
    }

    @Test
    void 邮箱找回正确验证码才设置数字密码() {
        when(redis.get("secondary:reset:code:fixture@example.com")).thenReturn("123456");
        assertThrows(RuntimeException.class, () -> service.resetSecondaryPassword(1L, "bad", "0012"));
        verify(mapper, never()).updateById(any(UserEntity.class));
        service.resetSecondaryPassword(1L, "123456", "0012");
        assertEquals(PasswordUtil.encryptPassword("0012", user.getSecondaryPasswordSalt()), user.getSecondaryPassword());
        verify(redis).delete("secondary:reset:code:fixture@example.com");
    }
}
