package top.aiolife.sso.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 身份凭据只用于本次注销复核，不接收目标用户 ID。 */
@Data
public class CloseAccountReq {
    @NotBlank private String confirmation;
    @Size(max = 200) private String password;
    @Size(max = 256) private String loginCode;
    @Size(max = 32) private String wechatScene;
    @Size(max = 64) private String wechatBrowserSecret;
}
