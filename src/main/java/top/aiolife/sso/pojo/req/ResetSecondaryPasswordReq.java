package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import top.aiolife.sso.util.SecondaryPasswordPolicy;

/**
 * 重置二级密码请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class ResetSecondaryPasswordReq {

    @NotBlank(message = "请输入邮箱验证码")
    private String code;

    @NotBlank(message = SecondaryPasswordPolicy.MESSAGE)
    @Pattern(regexp = SecondaryPasswordPolicy.PATTERN, message = SecondaryPasswordPolicy.MESSAGE)
    private String password;
}
