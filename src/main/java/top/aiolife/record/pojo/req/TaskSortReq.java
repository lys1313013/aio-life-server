package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** TaskSortReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskSortReq {

    @NotNull
    @Positive(message = "ID必须为正数")
    private Long id;

    /**
     * 列ID
     */
    @Positive(message = "任务列ID必须为正数")
    private Long columnId;

    /**
     * 排序
     */
    @NotNull
    private Integer sortOrder;
}
