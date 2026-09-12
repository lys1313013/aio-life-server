package top.aiolife.sso.pojo.req;

import lombok.Data;

/** 发送邮件验证码的请求参数。 */
@Data
public class SendEmailCodeReq {
    private String email;
}
