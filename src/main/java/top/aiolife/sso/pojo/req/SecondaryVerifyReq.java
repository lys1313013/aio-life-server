package top.aiolife.sso.pojo.req;

import lombok.Data;

/**
 * 二级密码验证请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SecondaryVerifyReq {

    private String password;

    /**
     * 要解锁的菜单路径
     */
    private String menuPath;
}
