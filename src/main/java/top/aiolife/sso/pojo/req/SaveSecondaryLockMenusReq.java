package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import top.aiolife.sso.util.SecondaryPasswordPolicy;

import java.util.List;

/**
 * 保存用户二级锁菜单请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SaveSecondaryLockMenusReq {

    private List<Long> menuIds;

    /**
     * 二级密码，修改菜单锁前必须验证
     */
    @NotBlank(message = SecondaryPasswordPolicy.MESSAGE)
    @Pattern(regexp = SecondaryPasswordPolicy.PATTERN, message = SecondaryPasswordPolicy.MESSAGE)
    private String secondaryPassword;
}
