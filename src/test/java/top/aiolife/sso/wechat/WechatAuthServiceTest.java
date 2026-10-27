package top.aiolife.sso.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
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
    WechatTicketStore.Ticket ticket = new WechatTicketStore.Ticket("openid", null);

    @Test void 已绑定用户直接登录不获取手机号() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("openid", null));
        UserEntity user = user();
        when(accounts.findByOpenid("openid")).thenReturn(user);
        when(sessions.complete(user, "ip", false)).thenReturn(token());
        var result = service.login("code", "ip");
        assertEquals("LOGGED_IN", result.getStatus());
        assertEquals("business-token", result.getAccessToken());
        verify(tickets, never()).issue(any());
    }

    @Test void 首次识别只返回票据而不是业务token() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("openid", null));
        when(tickets.issue(ticket)).thenReturn("ticket");
        var result = service.login("code", "ip");
        assertEquals("REGISTRATION_REQUIRED", result.getStatus());
        assertEquals("ticket", result.getLoginTicket());
        assertNull(result.getAccessToken());
        verifyNoInteractions(sessions);
    }











    @Test void 微信注册仅消费身份票据且提交后签发会话() {
        UserEntity user = user();
        when(tickets.consume("ticket")).thenReturn(ticket);
        when(accounts.register(ticket)).thenReturn(new WechatAccountService.Registration(user, true));
        when(sessions.complete(user, "ip", false)).thenReturn(token());
        var result = service.register(new WechatAuthRequests.Register("ticket", true, "2026-10-27"), "ip");
        assertEquals("LOGGED_IN", result.getStatus());
        assertTrue(result.isNewUser());
        assertEquals("business-token", result.getAccessToken());
        var order = inOrder(tickets, accounts, sessions);
        order.verify(tickets).rateLimit("register", "ip", 10);
        order.verify(tickets).consume("ticket");
        order.verify(accounts).register(ticket);
        order.verify(sessions).complete(user, "ip", false);
    }



    @Test void 微信注册并发冲突不签发会话或泄漏SQL() {
        when(tickets.consume("ticket")).thenReturn(ticket);
        when(accounts.register(ticket)).thenThrow(new DuplicateKeyException("private SQL"));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(new WechatAuthRequests.Register("ticket", true, "2026-10-27"), "ip"));
        assertFalse(error.getMessage().contains("private SQL"));
        verifyNoInteractions(sessions);
    }

    @Test void 未同意或旧协议不会消费注册票据() {
        for (var request : new WechatAuthRequests.Register[]{
                new WechatAuthRequests.Register("ticket", false, "2026-10-27"),
                new WechatAuthRequests.Register("ticket", true, "old"),
                new WechatAuthRequests.Register("ticket", null, null)}) {
            assertThrows(ResponseStatusException.class, () -> service.register(request, "ip"));
        }
        verifyNoInteractions(client, tickets, accounts, sessions);
    }

    private UserEntity user() { UserEntity user = new UserEntity(); user.setId(123L); user.setUsername("u_123"); return user; }
    private UserLoginVO token() { UserLoginVO token = new UserLoginVO(); token.setId(123L); token.setAccessToken("business-token"); return token; }
}
