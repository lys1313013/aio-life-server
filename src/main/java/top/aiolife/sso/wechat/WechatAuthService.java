package top.aiolife.sso.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.LoginSessionService;

/** 微信身份识别、手机号注册与老账号绑定，不使用手机号自动合并账号。 */
@Service
@RequiredArgsConstructor
public class WechatAuthService {
    private final WechatMiniClient client;
    private final WechatTicketStore tickets;
    private final WechatAccountService accounts;
    private final LoginSessionService sessions;

    public WechatLoginVO login(String code, String ip) {
        client.requireEnabled();
        tickets.rateLimit("login", ip, 30);
        WechatMiniClient.Identity identity = client.exchangeLogin(code);
        UserEntity user = accounts.findByOpenid(identity.openid());
        if (user != null) return authenticated(user, false, ip);
        return WechatLoginVO.pending("PHONE_REQUIRED", tickets.issue(
                new WechatTicketStore.Ticket(identity.openid(), identity.unionid(), null, null)));
    }

    public WechatLoginVO phoneLogin(WechatAuthRequests.PhoneLogin request, String ip) {
        client.requireEnabled();
        tickets.rateLimit("phone", ip, 10);
        WechatTicketStore.Ticket ticket = tickets.consume(request.loginTicket());
        if (ticket.phone() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请验证原账号后绑定微信");
        }
        ticket = ticket.withPhone(client.exchangePhone(request.phoneCode()));
        WechatAccountService.Registration registration;
        try {
            registration = accounts.register(ticket);
        } catch (DuplicateKeyException e) {
            // 唯一索引处理不同票据之间的并发竞争；事务已回滚，不重复创建或自动合并。
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号绑定状态已变化，请重新微信登录");
        }
        if (registration.user() == null) {
            return WechatLoginVO.pending("BIND_REQUIRED", tickets.issue(ticket));
        }
        return authenticated(registration.user(), registration.newUser(), ip);
    }

    public WechatLoginVO bind(long userId, WechatAuthRequests.Bind request, String ip) {
        client.requireEnabled();
        tickets.rateLimit("bind", ip, 10);
        WechatTicketStore.Ticket ticket = tickets.consume(request.loginTicket());
        UserEntity user;
        try {
            user = accounts.bind(userId, request.password(), ticket);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号绑定状态已变化，请重新微信登录");
        }
        return authenticated(user, false, ip);
    }

    public void initializePassword(long userId, WechatAuthRequests.InitializePassword request, String ip) {
        client.requireEnabled();
        tickets.rateLimit("password", ip, 10);
        WechatMiniClient.Identity identity = client.exchangeLogin(request.loginCode());
        accounts.initializePassword(userId, identity.openid(), request.newPassword());
    }

    private WechatLoginVO authenticated(UserEntity user, boolean newUser, String ip) {
        WechatLoginVO result = WechatLoginVO.loggedIn(sessions.complete(user, ip, false), newUser, user.getUsername());
        result.setHasPassword(org.springframework.util.StringUtils.hasText(user.getPassword()));
        return result;
    }
}
