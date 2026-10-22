package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** TaskColumnCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskColumnCreateReq {

    /**
     * 列标题
     */
    @NotBlank(message = "任务列标题不能为空")
    private String title;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 背景颜色
     */
    private String bgColor;
}
