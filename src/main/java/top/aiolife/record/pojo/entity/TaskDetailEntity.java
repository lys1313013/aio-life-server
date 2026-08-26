package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 23:19
 */
@Getter
@Setter
@TableName("task_detail")
public class TaskDetailEntity extends BaseEntity {

    private Long taskId;

    private Long userId;

    private String content;
    private Integer isCompleted;
    private Integer sort;
    /**
     * 优先级: 1-高, 10-中, 20-低
     */
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
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String taskName;
}
