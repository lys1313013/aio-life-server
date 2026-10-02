package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** TaskDetailVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDetailVO {

    private Long id;

    private Long taskId;

    private String content;

    private Integer isCompleted;

    private Integer sort;

    private Integer priority;

    /**
     * 是否关注: 0-未关注, 1-已关注
     */
    private Integer isStarred;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 冗余字段：所属任务名称
     */
    private String taskName;
}
