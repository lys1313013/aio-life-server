package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 操作与访问日志。仅保存行为元数据与白名单脱敏参数，不保存响应正文。 */
@Getter
@Setter
@TableName("sys_activity_log")
public class ActivityLogEntity extends BaseEntity {
    private String logType;
    private Long userId;
    private String username;
    private String nickname;
    private String ipAddress;
    private String browser;
    private String functionName;
    private String functionItem;
    private String accessType;
    private String requestPath;
    private String requestMethod;
    private String requestParams;
    private LocalDateTime requestTime;
    private Integer durationMs;
    private String serverIp;
    private Boolean success;
}
