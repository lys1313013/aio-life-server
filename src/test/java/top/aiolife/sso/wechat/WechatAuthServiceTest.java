package top.aiolife.sso.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.service.LoginSessionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatAuthServiceTest {
    WechatMiniClient client = mock(WechatMiniClient.class);
    WechatTicketStore tickets = mock(WechatTicketStore.class);
    WechatAccountService accounts = mock(WechatAccountService.class);
    LoginSessionService sessions = mock(LoginSessionService.class);
    WechatAuthService service = new WechatAuthService(client, tickets, accounts, sessions);
    WechatTicketStore.Ticket ticket = new WechatTicketStore.Ticket("openid", null, null, null);

    @Test void 已绑定用户直接登录不获取手机号() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("openid", null));
        UserEntity user = user();
        when(accounts.findByOpenid("openid")).thenReturn(user);
        when(sessions.complete(user, "ip", false)).thenReturn(token());
        var result = service.login("code", "ip");
        assertEquals("LOGGED_IN", result.getStatus());
        assertEquals("business-token", result.getAccessToken());
        verify(client, never()).exchangePhone(anyString());
        verify(tickets, never()).issue(any());
    }

    @Test void 首次识别只返回票据而不是业务token() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("openid", null));
        when(tickets.issue(ticket)).thenReturn("ticket");
        var result = service.login("code", "ip");
        assertEquals("PHONE_REQUIRED", result.getStatus());
        assertEquals("ticket", result.getLoginTicket());
        assertNull(result.getAccessToken());
        verifyNoInteractions(sessions);
    }

    @Test void 手机号匹配已有用户只返回绑定状态() {
        when(tickets.consume("ticket")).thenReturn(ticket);
        when(client.exchangePhone("phone")).thenReturn(new WechatMiniClient.Phone("86", "13800138000"));
        when(accounts.register(any())).thenReturn(new WechatAccountService.Registration(null, false));
        when(tickets.issue(any())).thenReturn("bind-ticket");
        var result = service.phoneLogin(new WechatAuthRequests.PhoneLogin("ticket", "phone"), "ip");
        assertEquals("BIND_REQUIRED", result.getStatus());
        assertNull(result.getAccessToken());
        verifyNoInteractions(sessions);
    }

    @Test void 创建提交完成后才签发token() {
        UserEntity user = user();
        when(tickets.consume("ticket")).thenReturn(ticket);
        when(client.exchangePhone("phone")).thenReturn(new WechatMiniClient.Phone("86", "13800138000"));
        when(accounts.register(any())).thenReturn(new WechatAccountService.Registration(user, true));
        when(sessions.complete(user, "ip", false)).thenReturn(token());
        var result = service.phoneLogin(new WechatAuthRequests.PhoneLogin("ticket", "phone"), "ip");
        assertTrue(result.isNewUser());
        assertFalse(result.isHasPassword());
        var order = inOrder(accounts, sessions);
        order.verify(accounts).register(any());
        order.verify(sessions).complete(user, "ip", false);
    }

    @Test void 重复票据在调用微信手机号前被拒绝() {
        when(tickets.consume("used")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST));
        assertThrows(ResponseStatusException.class, () -> service.phoneLogin(new WechatAuthRequests.PhoneLogin("used", "phone"), "ip"));
        verify(client, never()).exchangePhone(anyString());
        verifyNoInteractions(accounts, sessions);
    }

    @Test void 绑定票据不能当作注册票据() {
        when(tickets.consume("ticket")).thenReturn(ticket.withPhone(new WechatMiniClient.Phone("86", "13800138000")));
        assertThrows(ResponseStatusException.class, () -> service.phoneLogin(new WechatAuthRequests.PhoneLogin("ticket", "phone"), "ip"));
        verify(client, never()).exchangePhone(anyString());
    }

    @Test void 并发唯一键冲突不签发token且不泄漏SQL() {
        when(tickets.consume("ticket")).thenReturn(ticket);
        when(client.exchangePhone("phone")).thenReturn(new WechatMiniClient.Phone("86", "13800138000"));
        when(accounts.register(any())).thenThrow(new DuplicateKeyException("private SQL"));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.phoneLogin(new WechatAuthRequests.PhoneLogin("ticket", "phone"), "ip"));
        assertFalse(error.getMessage().contains("private SQL"));
        verifyNoInteractions(sessions);
    }

    private UserEntity user() { UserEntity user = new UserEntity(); user.setId(123L); user.setUsername("u_123"); return user; }
    private UserLoginVO token() { UserLoginVO token = new UserLoginVO(); token.setId(123L); token.setAccessToken("business-token"); return token; }
}
