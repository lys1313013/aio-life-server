package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** TaskCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskCreateReq {

    /**
     * 任务内容
     */
    private String content;

    /**
     * 任务明细
     */
    private String detail;

    /**
     * 列ID
     */
    private Long columnId;

    /**
     * 目标完成时间
     */
    private LocalDateTime dueDate;

    /**
     * 排序
     */
    private Integer sortOrder;
}
