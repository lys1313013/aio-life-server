package top.aiolife.scheduling.pojo.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ScheduledJobLogVO {
    private Long id;
    private Long jobId;
    private String triggerType;
    private String status;
    private LocalDateTime scheduledTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String message;
    private Long createUser;
}
