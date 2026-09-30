package top.aiolife.sso.wechat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import top.aiolife.sso.pojo.vo.UserLoginVO;

/** LOGGED_IN 才包含业务 Token；其他状态的票据不能访问业务接口。 */
@Getter
@Setter
public class WechatLoginVO extends UserLoginVO {
    private String status;
    private String loginTicket;
    private Integer expiresIn;
    private boolean newUser;
    private boolean hasPassword;
    private String accountUsername;

    public static WechatLoginVO pending(String status, String ticket) {
        WechatLoginVO result = new WechatLoginVO();
        result.setStatus(status);
        result.setLoginTicket(ticket);
        result.setExpiresIn(300);
        return result;
    }

    public static WechatLoginVO loggedIn(UserLoginVO login, boolean newUser, String username) {
        WechatLoginVO result = new WechatLoginVO();
        BeanUtils.copyProperties(login, result);
        result.setStatus("LOGGED_IN");
        result.setNewUser(newUser);
        result.setAccountUsername(username);
        return result;
    }
}
