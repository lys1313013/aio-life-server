package top.aiolife.sso.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.service.LoginSessionService;

@Service
@RequiredArgsConstructor
public class WechatWebLoginService {
    private final WechatMiniClient client;
    private final WechatMiniProperties properties;
    private final WechatTicketStore rate;
    private final WechatWebTicketStore tickets;
    private final UserMapper users;
    private final LoginSessionService sessions;

    public boolean enabled() { return client.enabled() && properties.isWebScanEnabled(); }

    private void requireEnabled() {
        if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "网页微信扫码登录暂未开放");
    }

    public Challenge create(String ip) {
        requireEnabled();
        rate.rateLimit("web-create", ip, 10);
        rate.rateLimit("web-create-global", "all", 100);
        String scene = WechatWebTicketStore.random(16);
        String secret = WechatWebTicketStore.random(32);
        String image = client.webLoginQrCode(scene);
        // 微信取码成功后才开始计时，失败不留下可使用的登录请求。
        tickets.create(scene, secret);
        return new Challenge(scene, secret, image, WechatWebTicketStore.EXPIRES_IN);
    }

    public String status(String scene, String secret) {
        requireEnabled();
        return tickets.browser(scene, secret, "status");
    }

    public void revoke(String scene, String secret) {
        requireEnabled();
        tickets.browser(scene, secret, "revoke");
    }

    public String scan(String scene, long userId) {
        requireEnabled();
        activeUser(userId);
        return tickets.mobile(scene, userId, "scan");
    }

    public String confirm(String scene, long userId, String loginCode, String ip) {
        requireEnabled();
        rate.rateLimit("web-confirm", ip, 20);
        UserEntity user = activeUser(userId);
        // 不能仅靠业务 Token 冒充小程序确认，校验新鲜微信凭证与当前账号的绑定。
        var identity = client.exchangeLogin(loginCode);
        if (!identity.openid().equals(user.getWechatOpenid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前微信与登录账号不一致，请先绑定微信后重试");
        }
        return tickets.mobile(scene, userId, "confirm");
    }

    public String cancel(String scene, long userId) {
        requireEnabled();
        return tickets.mobile(scene, userId, "cancel");
    }

    public UserLoginVO exchange(String scene, String secret, String ip) {
        requireEnabled();
        String result = tickets.browser(scene, secret, "exchange");
        if (!result.startsWith("USER:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录请求未确认或已失效，请重新扫码");
        }
        // 再查数据库，防止确认后账号被删除；消费成功才允许签发一次 Web 会话。
        UserEntity user = activeUser(Long.parseLong(result.substring(5)));
        return sessions.complete(user, ip, true);
    }

    private UserEntity activeUser(long userId) {
        UserEntity user = users.selectById(userId);
        if (user == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号不存在或已删除");
        return user;
    }

    public record Challenge(String scene, String browserSecret, String qrCode, int expiresIn) {}
}
