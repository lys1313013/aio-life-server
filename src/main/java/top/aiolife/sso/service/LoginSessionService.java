package top.aiolife.sso.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.pojo.entity.LoginLogEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.vo.UserLoginVO;

import java.util.Arrays;
import java.util.Collections;

/** 所有认证方式共用的业务会话签发；调用前必须完成身份验证和数据库提交。 */
@Service
@RequiredArgsConstructor
public class LoginSessionService {
    private final LoginLogMapper loginLogMapper;

    public UserLoginVO complete(UserEntity user, String ip, boolean browserCookie) {
        return complete(user, ip, browserCookie, false);
    }

    /** 扫码兑换先保存结果，再通过响应体交付；不能提前写 Cookie 或响应头。 */
    public UserLoginVO completeQr(UserEntity user, String ip) {
        return complete(user, ip, false, true);
    }

    private UserLoginVO complete(UserEntity user, String ip, boolean browserCookie, boolean issueOnly) {
        LoginLogEntity log = new LoginLogEntity();
        log.setUserId(user.getId());
        log.setUsername(user.getUsername());
        log.setIpAddress(ip);
        loginLogMapper.insert(log);
        String token;
        if (issueOnly) {
            token = StpUtil.getStpLogic().createLoginSession(user.getId(),
                    new SaLoginModel().setDevice("web"));
        } else {
            StpUtil.login(user.getId());
            token = StpUtil.getTokenValue();
        }
        if (browserCookie) {
            String prefix = SaManager.getConfig().getTokenPrefix();
            String value = StringUtils.hasText(prefix) ? prefix + " " + token : token;
            SaHolder.getResponse().addCookie(StpUtil.getTokenName(), value, "/", null,
                    (int) SaManager.getConfig().getTimeout());
        }
        UserLoginVO result = new UserLoginVO();
        result.setId(user.getId());
        result.setRealName(user.getNickname());
        // 保持原账号密码登录的展示字段契约。
        result.setUsername(user.getNickname());
        result.setRoles(StringUtils.hasText(user.getRole())
                ? Arrays.asList(user.getRole().split(",")) : Collections.singletonList("user"));
        result.setAccessToken(token);
        return result;
    }
}
