package top.aiolife.scheduling.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 定时任务执行记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_scheduled_job_log")
public class ScheduledJobLogEntity extends BaseEntity {
    private Long jobId;
    private String triggerKey;
    private String triggerType;
    private String status;
    private LocalDateTime scheduledTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String message;
    private String instanceId;
}
