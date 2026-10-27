package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 注册请求参数
 *
 * @author Lys
 * @date 2026/03/01
 */
@Data
public class RegisterReq {

    /** 必须明确同意本次注册展示的协议版本。 */
    @NotNull @AssertTrue
    private Boolean agreedPolicy;
    @NotBlank
    private String policyVersion;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String code;
}
