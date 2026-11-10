package top.aiolife.sso.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** API Key 备注修改请求，不允许修改密钥或有效期。 */
@Data
public class ApiKeyUpdateReq {
    @NotBlank(message = "请输入备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
