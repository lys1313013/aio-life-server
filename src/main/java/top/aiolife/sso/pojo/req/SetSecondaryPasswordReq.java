package top.aiolife.sso.pojo.req;

import lombok.Data;

/**
 * 设置二级密码请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SetSecondaryPasswordReq {

    private String password;

    /**
     * 旧密码（修改时必传）
     */
    private String oldPassword;
}
