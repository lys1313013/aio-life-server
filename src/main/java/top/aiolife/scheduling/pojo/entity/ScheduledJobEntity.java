package top.aiolife.scheduling.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 定时任务配置。 */
@Getter
@Setter
@ToString
@TableName("sys_scheduled_job")
public class ScheduledJobEntity extends BaseEntity {
    private String jobCode;
    private String name;
    private String cron;
    private String zoneId;
    private Integer isEnabled;
    private Integer revision;
}
