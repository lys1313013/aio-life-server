package top.aiolife.llm.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** LLMKeyCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMKeyCreateReq {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * API密钥（加密存储）
     */
    private String apiKey;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * 是否默认
     */
    private Integer isDefault;
}
