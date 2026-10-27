package top.aiolife.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.AccountErasureMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.CloseAccountReq;
import top.aiolife.sso.util.PasswordUtil;
import top.aiolife.sso.wechat.WechatMiniClient;
import top.aiolife.sso.wechat.WechatTicketStore;
import top.aiolife.sso.wechat.WechatWebLoginService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountClosureServiceTest {
    UserMapper users = mock(UserMapper.class);
    AccountErasureMapper erasure = mock(AccountErasureMapper.class);
    WechatMiniClient wechat = mock(WechatMiniClient.class);
    WechatTicketStore limits = mock(WechatTicketStore.class);
    WechatWebLoginService web = mock(WechatWebLoginService.class);
    AccountClosureService service = new AccountClosureService(users, erasure, wechat, limits, web);
    UserEntity user = new UserEntity();
    @BeforeEach void setup() {
        user.setId(7L); user.setPasswordSalt("fixture-salt");
        user.setPassword(PasswordUtil.encryptPassword("fixture-password", "fixture-salt"));
        user.setWechatOpenid("fixture-owner");
        when(users.selectForAuthUpdate(7L)).thenReturn(user);
        when(erasure.request(eq(7L), any(), any())).thenReturn(1);
    }
    CloseAccountReq request() { var req = new CloseAccountReq(); req.setConfirmation("注销账号"); return req; }
    @Test void 缺少确认和错误密码都不受理注销() {
        var req = request(); req.setConfirmation("退出");
        assertThrows(ResponseStatusException.class, () -> service.close(7L, req));
        verifyNoInteractions(limits, erasure);
        req.setConfirmation("注销账号"); req.setPassword("wrong");
        assertThrows(ResponseStatusException.class, () -> service.close(7L, req));
        verifyNoInteractions(erasure);
    }
    @Test void 正确密码只注销当前账号并撤销全部设备会话() {
        var req = request(); req.setPassword("fixture-password");
        try (var stp = mockStatic(StpUtil.class)) {
            service.close(7L, req);
            verify(erasure).request(eq(7L), any(), any());
            verify(erasure).cancelExports(7L);
            verify(erasure).cancelNotifications(7L);
            stp.verify(() -> StpUtil.logout(7L));
        }
    }
    @Test void 微信重新核验必须匹配当前账号且无需密码() {
        var req = request(); req.setLoginCode("fixture-code");
        when(wechat.exchangeLogin("fixture-code")).thenReturn(new WechatMiniClient.Identity("other", null));
        assertThrows(ResponseStatusException.class, () -> service.close(7L, req));
        verifyNoInteractions(erasure);
        when(wechat.exchangeLogin("fixture-code")).thenReturn(new WechatMiniClient.Identity("fixture-owner", null));
        user.setPassword(null);
        try (var stp = mockStatic(StpUtil.class)) { service.close(7L, req); stp.verify(() -> StpUtil.logout(7L)); }
    }
    @Test void Web扫码确认他人账号不能注销当前账号() {
        var req = request(); req.setWechatScene("scene"); req.setWechatBrowserSecret("secret");
        when(web.consumeConfirmedIdentity("scene", "secret")).thenReturn(8L);
        assertThrows(ResponseStatusException.class, () -> service.close(7L, req));
        verifyNoInteractions(erasure);
        when(web.consumeConfirmedIdentity("scene", "secret")).thenReturn(7L);
        try (var stp = mockStatic(StpUtil.class)) { service.close(7L, req); stp.verify(() -> StpUtil.logout(7L)); }
    }
    @Test void 会话撤销失败不能被伪装成受理成功() {
        var req = request(); req.setPassword("fixture-password");
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.logout(7L)).thenThrow(new IllegalStateException("fixture"));
            assertThrows(IllegalStateException.class, () -> service.close(7L, req));
        }
    }
}
