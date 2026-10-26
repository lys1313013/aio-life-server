package top.aiolife.sso.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.MybatisPlusConfig;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.util.PasswordUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 在测试 MySQL 上验证真实索引、逻辑删除及事务内账号归属，不访问微信。 */
@ActiveProfiles("test")
@SpringBootTest(classes = WechatAccountIntegrationTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class WechatAccountIntegrationTest {
    @Autowired WechatAccountService service;
    @Autowired UserMapper users;
    @Autowired JdbcTemplate jdbc;





    @Test void 无手机号的微信注册不伪造验证时间且重复注册返回原账号() {
        var ticket = ticket(null, null);
        var registered = service.register(ticket);
        var user = users.selectById(registered.user().getId());
        assertTrue(registered.newUser());
        assertNull(user.getPhone());
        assertNull(user.getPhoneCountryCode());
        assertNull(user.getPhoneVerifiedAt());
        assertNull(user.getPassword());
        assertEquals(ticket.openid(), user.getWechatOpenid());
        assertEquals("user", user.getRole());
        var repeated = service.register(ticket);
        assertFalse(repeated.newUser());
        assertEquals(user.getId(), repeated.user().getId());
    }



    @Test void openid按大小写区分() {
        String id = "wx_" + UUID.randomUUID();
        var first = service.register(new WechatTicketStore.Ticket(id + "A", null)).user();
        var second = service.register(new WechatTicketStore.Ticket(id + "a", null)).user();
        assertNotEquals(first.getId(), second.getId());
    }

    @Test void 多次注销后可以重新注册同一手机号与微信() {
        var identity = ticket("86", "13800138004");
        for (int i = 0; i < 3; i++) {
            var user = service.register(identity).user();
            assertNotNull(user);
            assertEquals(1, users.deleteById(user.getId()));
        }
        assertNotNull(service.register(identity).user());
    }



    @Test void 老账号验证密码后绑定且保留原ID和资料() {
        UserEntity user = legacy();
        var result = service.bind(user.getId(), "old-password", ticket("86", "13800138007"));
        assertEquals(user.getId(), result.getId());
        assertEquals("原账号昵称", result.getNickname());
        assertEquals("test@example.com", result.getEmail());
        assertNotNull(result.getWechatOpenid());
    }

    @Test void 错误密码不能绑定() {
        UserEntity user = legacy();
        assertThrows(ResponseStatusException.class, () -> service.bind(user.getId(), "wrong", ticket("86", "13800138008")));
        assertNull(users.selectById(user.getId()).getWechatOpenid());
    }

    @Test void 不能覆盖原微信() {
        UserEntity user = legacy();
        service.bind(user.getId(), "old-password", ticket("86", "13800138009"));
        assertThrows(ResponseStatusException.class, () -> service.bind(user.getId(), "old-password", ticket("86", "13800138009")));
        assertNotNull(users.selectById(user.getId()).getWechatOpenid());
    }

    @Test void 首次设置密码必须是同一微信且不能覆盖已有密码() {
        var identity = ticket("86", "13800138010");
        UserEntity user = service.register(identity).user();
        assertThrows(ResponseStatusException.class, () -> service.initializePassword(user.getId(), "other", "new-password"));
        service.initializePassword(user.getId(), identity.openid(), "new-password");
        UserEntity saved = users.selectById(user.getId());
        assertEquals(PasswordUtil.encryptPassword("new-password", saved.getPasswordSalt()), saved.getPassword());
        assertThrows(ResponseStatusException.class, () -> service.initializePassword(user.getId(), identity.openid(), "replacement"));
    }

    private WechatTicketStore.Ticket ticket(String country, String phone) {
        return new WechatTicketStore.Ticket("wx_" + UUID.randomUUID(), null);
    }

    private UserEntity legacy() {
        UserEntity user = new UserEntity();
        user.setUsername("test_" + UUID.randomUUID());
        user.setNickname("原账号昵称");
        user.setEmail("test@example.com");
        user.setPasswordSalt("salt");
        user.setPassword(PasswordUtil.encryptPassword("old-password", "salt"));
        users.insert(user);
        return user;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({MybatisPlusConfig.class, WechatAccountService.class})
    static class Application {}
}
