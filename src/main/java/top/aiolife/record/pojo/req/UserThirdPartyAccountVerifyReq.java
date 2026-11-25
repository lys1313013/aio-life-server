package top.aiolife.record.pojo.req;

import com.alibaba.fastjson2.annotation.JSONField;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 验证表单中的账号和凭证，编辑绑定时可留空使用已保存的凭证。 */
@Getter
@Setter
public class UserThirdPartyAccountVerifyReq {
    private Long id;

    @NotBlank
    private String platform;

    private String platformUsername;

    @JSONField(serialize = false)
    private String accessToken;
}
