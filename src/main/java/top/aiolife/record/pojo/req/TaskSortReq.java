package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TaskSortReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskSortReq {

    @NotNull
    private Long id;

    /**
     * 列ID
     */
    private Long columnId;

    /**
     * 排序
     */
    @NotNull
    private Integer sortOrder;
}
