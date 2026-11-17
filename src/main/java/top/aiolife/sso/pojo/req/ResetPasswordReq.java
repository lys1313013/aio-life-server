package top.aiolife.sso.pojo.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求参数
 *
 * @author Lys
 * @date 2026/03/01
 */
@Data
public class ResetPasswordReq {

    /**
     * 邮箱
     */
    @Size(max = 254)
    private String email;

    /**
     * 密码
     */
    private String password;

    /**
     * 验证码
     */
    private String code;
}
