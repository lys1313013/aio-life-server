package top.aiolife.sso.wechat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.LoginSessionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatWebLoginServiceTest {
    WechatMiniClient client = mock(WechatMiniClient.class);
    WechatMiniProperties properties = new WechatMiniProperties();
    WechatTicketStore rate = mock(WechatTicketStore.class);
    WechatWebTicketStore tickets = mock(WechatWebTicketStore.class);
    UserMapper users = mock(UserMapper.class);
    LoginSessionService sessions = mock(LoginSessionService.class);
    WechatWebLoginService service = new WechatWebLoginService(client, properties, rate, tickets, users, sessions);
    UserEntity user = new UserEntity();
    @BeforeEach void setup() {
        properties.setWebScanEnabled(true);
        when(client.enabled()).thenReturn(true);
        user.setId(13L); user.setWechatOpenid("wx-owner");
        when(users.selectById(13L)).thenReturn(user);
    }
    @Test void 默认关闭且关闭时不取码() {
        properties.setWebScanEnabled(false);
        assertFalse(service.enabled());
        assertThrows(ResponseStatusException.class, () -> service.create("ip"));
        verify(client, never()).webLoginQrCode(anyString());
        verifyNoInteractions(tickets);
    }
    @Test void 二维码只含场景且成功取码才保存请求() {
        when(client.webLoginQrCode(anyString())).thenReturn("data:image/png;base64,fixture");
        var challenge = service.create("ip");
        assertTrue(challenge.scene().matches("[a-f0-9]{32}"));
        assertTrue(challenge.browserSecret().matches("[a-f0-9]{64}"));
        var order = inOrder(client, tickets);
        order.verify(client).webLoginQrCode(challenge.scene());
        order.verify(tickets).create(challenge.scene(), challenge.browserSecret());
        verify(rate).rateLimit("web-create", "ip", 10);
    }
    @Test void 取码失败不创建请求() {
        when(client.webLoginQrCode(anyString())).thenThrow(new IllegalStateException("upstream"));
        assertThrows(IllegalStateException.class, () -> service.create("ip"));
        verifyNoInteractions(tickets, sessions);
    }
    @Test void 其他微信身份即使有会话也不能确认() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("other", null));
        assertThrows(ResponseStatusException.class, () -> service.confirm("scene", 13, "code", "ip"));
        verifyNoInteractions(tickets, sessions);
    }
    @Test void 确认只能修改扫码状态不向手机发网页Token() {
        when(client.exchangeLogin("code")).thenReturn(new WechatMiniClient.Identity("wx-owner", null));
        when(tickets.mobile("scene", 13, "confirm")).thenReturn("CONFIRMED");
        assertEquals("CONFIRMED", service.confirm("scene", 13, "code", "ip"));
        verifyNoInteractions(sessions);
    }
    @Test void 未确认取消过期重放均不能签发会话() {
        for (String status : new String[]{"WAITING", "SCANNED", "CANCELLED", "EXPIRED", "CONSUMED"}) {
            when(tickets.browser("scene", "secret", "exchange")).thenReturn(status);
            assertThrows(ResponseStatusException.class, () -> service.exchange("scene", "secret", "ip"));
        }
        verifyNoInteractions(sessions);
    }
    @Test void 兑换时重新检查已删除账号() {
        when(tickets.browser("scene", "secret", "exchange")).thenReturn("USER:14");
        assertThrows(ResponseStatusException.class, () -> service.exchange("scene", "secret", "ip"));
        verifyNoInteractions(sessions);
    }
    @Test void 原子消费之后复用浏览器会话签发() {
        when(tickets.browser("scene", "secret", "exchange")).thenReturn("USER:13");
        service.exchange("scene", "secret", "ip");
        var order = inOrder(tickets, users, sessions);
        order.verify(tickets).browser("scene", "secret", "exchange");
        order.verify(users).selectById(13L);
        order.verify(sessions).complete(user, "ip", true);
    }
}
