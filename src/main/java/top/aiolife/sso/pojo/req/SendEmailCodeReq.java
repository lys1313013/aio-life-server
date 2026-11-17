package top.aiolife.sso.pojo.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发送邮件验证码的请求参数。 */
@Data
public class SendEmailCodeReq {
    @Size(max = 254)
    private String email;
}
