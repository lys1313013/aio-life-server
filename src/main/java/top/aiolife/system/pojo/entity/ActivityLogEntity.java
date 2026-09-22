package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 操作与访问日志。只保存行为元数据，不保存请求参数或响应正文。 */
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
    private Boolean success;
}
