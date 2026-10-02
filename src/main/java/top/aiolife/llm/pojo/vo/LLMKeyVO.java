package top.aiolife.llm.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** LLMKeyVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMKeyVO {

    private Long id;

    /**
     * 模型名称
     */
    private String modelName;

    private boolean hasApiKey;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * 是否默认
     */
    private Integer isDefault;
}
