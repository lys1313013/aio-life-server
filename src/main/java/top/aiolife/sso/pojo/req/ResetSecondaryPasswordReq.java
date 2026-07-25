package top.aiolife.sso.pojo.req;

import lombok.Data;

/**
 * 重置二级密码请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class ResetSecondaryPasswordReq {

    private String code;

    private String password;
}
