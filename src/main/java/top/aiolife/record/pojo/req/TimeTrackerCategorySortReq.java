package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TimeTrackerCategorySortReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeTrackerCategorySortReq {

    private Long id;

    /**
     * 模板ID，指向被覆盖的公共分类ID
     */
    private Long templateId;

    /**
     * 排序权重
     */
    @NotNull
    private Integer sort;
}
