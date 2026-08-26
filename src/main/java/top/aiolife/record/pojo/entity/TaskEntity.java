package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务实体类
 *
 * @author Lys
 * @date 2025/04/10 22:45
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class TaskEntity extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

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

    @TableField(exist = false)
    private Integer unCompletedCount;
}
