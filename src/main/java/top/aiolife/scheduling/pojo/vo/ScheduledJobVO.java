package top.aiolife.scheduling.pojo.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ScheduledJobVO {
    private Long id;
    private String jobCode;
    private String name;
    private String cron;
    private String zoneId;
    private Boolean enabled;
    private Integer revision;
    private boolean schedulerEnabled;
    private LocalDateTime nextExecutionTime;
}
