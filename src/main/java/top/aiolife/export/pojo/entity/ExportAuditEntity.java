package top.aiolife.export.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import top.aiolife.core.pojo.entity.BaseEntity;

import java.time.LocalDateTime;

/** 导出持久化记录；只在存储层使用，不直接返回客户端。 */
@Getter
@Setter
@TableName("data_export_audit_event")
public class ExportAuditEntity extends BaseEntity {
    private Long jobId;
    private Long userId;
    private String eventType;
    private String resultCode;
    private String requestId;
    private String clientIp;
    private String traceId;
    private String details;
    private LocalDateTime eventTime;
}
