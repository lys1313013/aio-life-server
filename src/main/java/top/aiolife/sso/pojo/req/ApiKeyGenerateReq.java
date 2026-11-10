package top.aiolife.sso.pojo.req;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API Key 生成请求参数
 *
 * @author Lys
 * @date 2026/03/09
 */
@Data
public class ApiKeyGenerateReq {
    /**
     * 备注
     */
    @NotBlank(message = "请输入备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    /**
     * 过期天数
     */
    private Integer expireDays;
}
