package top.aiolife.scheduling.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 定时任务配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_scheduled_job")
public class ScheduledJobEntity extends BaseEntity {
    private String jobCode;
    private String name;
    private String cron;
    private String zoneId;
    private Boolean enabled;
    private Integer revision;
}
