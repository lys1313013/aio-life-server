package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import top.aiolife.sso.util.SecondaryPasswordPolicy;

/**
 * 设置二级密码请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SetSecondaryPasswordReq {

    @NotBlank(message = SecondaryPasswordPolicy.MESSAGE)
    @Pattern(regexp = SecondaryPasswordPolicy.PATTERN, message = SecondaryPasswordPolicy.MESSAGE)
    private String password;

    /**
     * 旧密码（修改时必传）
     */
    @Pattern(regexp = SecondaryPasswordPolicy.PATTERN, message = SecondaryPasswordPolicy.MESSAGE)
    private String oldPassword;
}
