package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TaskColumnSortReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskColumnSortReq {

    @NotNull
    private Long id;

    /**
     * 排序
     */
    @NotNull
    private Integer sortOrder;
}
