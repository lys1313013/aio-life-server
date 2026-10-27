package top.aiolife.sso.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.AccountErasureMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.req.CloseAccountReq;
import top.aiolife.sso.util.PasswordUtil;
import top.aiolife.sso.wechat.WechatMiniClient;
import top.aiolife.sso.wechat.WechatTicketStore;
import top.aiolife.sso.wechat.WechatWebLoginService;

/** 自助注销与管理员软删除分开：先停用，持久化清理任务由后台重试完成。 */
@Service
@RequiredArgsConstructor
public class AccountClosureService {
    private final UserMapper users;
    private final AccountErasureMapper erasure;
    private final WechatMiniClient wechat;
    private final WechatTicketStore limits;
    private final WechatWebLoginService webWechat;

    public static long requestUser() {
        if (StpUtil.isSwitch() || Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用本人账号登录后注销");
        return StpUtil.getLoginIdAsLong();
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#userId")
    public void close(long userId, CloseAccountReq request) {
        if (!"注销账号".equals(request.getConfirmation()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入“注销账号”确认操作");
        limits.rateLimit("account-closure", Long.toString(userId), 5);
        // 微信网络请求先完成，再锁定当前账号并核对，不能采信客户端提供的 openid。
        String openid = StringUtils.hasText(request.getLoginCode())
                ? wechat.exchangeLogin(request.getLoginCode()).openid() : null;
        Long scannedUser = StringUtils.hasText(request.getWechatScene())
                ? webWechat.consumeConfirmedIdentity(request.getWechatScene(), request.getWechatBrowserSecret()) : null;
        var user = users.selectForAuthUpdate(userId);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在或已注销");
        boolean passwordMatches = StringUtils.hasText(request.getPassword()) && StringUtils.hasText(user.getPassword())
                && user.getPassword().equals(PasswordUtil.encryptPassword(request.getPassword(), user.getPasswordSalt()));
        boolean wechatMatches = StringUtils.hasText(openid) && openid.equals(user.getWechatOpenid());
        boolean scanMatches = scannedUser != null && scannedUser.longValue() == userId;
        if (!passwordMatches && !wechatMatches && !scanMatches)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "身份验证失败，请使用当前密码或本人绑定的微信重试");
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (erasure.request(userId, now, now.plusMinutes(5)) != 1) throw new IllegalStateException("注销受理失败，请重试");
        // 预留在途请求收尾时间；有导出租约时清理工作器还会继续等待。
        erasure.cancelExports(userId);
        erasure.cancelNotifications(userId);
        // 失败时事务回滚，客户端保留表单；即使凭据缓存残留，AccountStatusGuard 也会拒绝停用账号。
        StpUtil.logout(userId);
    }
}
