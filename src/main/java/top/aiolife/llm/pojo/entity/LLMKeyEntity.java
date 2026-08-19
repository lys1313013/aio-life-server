package top.aiolife.llm.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.AuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("llm_key")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMKeyEntity extends AuditEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户ID
     */
    private Long userId;

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

    @TableLogic
    private Integer isDeleted;

    @JsonSetter
    public void setIsDefault(Object value) {
        if (value instanceof Boolean) {
            this.isDefault = ((Boolean) value) ? 1 : 0;
        } else if (value instanceof Integer) {
            this.isDefault = (Integer) value;
        } else if (value instanceof String) {
            this.isDefault = Boolean.parseBoolean((String) value) ? 1 : 0;
        }
    }
}
