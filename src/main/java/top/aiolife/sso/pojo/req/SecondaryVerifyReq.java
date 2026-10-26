package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import top.aiolife.sso.util.SecondaryPasswordPolicy;

/**
 * 二级密码验证请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SecondaryVerifyReq {

    @NotBlank(message = SecondaryPasswordPolicy.MESSAGE)
    @Pattern(regexp = SecondaryPasswordPolicy.PATTERN, message = SecondaryPasswordPolicy.MESSAGE)
    private String password;

    /**
     * 要解锁的菜单路径
     */
    private String menuPath;
}
