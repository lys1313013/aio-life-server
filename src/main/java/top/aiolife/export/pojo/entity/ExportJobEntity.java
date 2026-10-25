package top.aiolife.export.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import top.aiolife.core.pojo.entity.BaseEntity;

import java.time.LocalDateTime;

/** 导出持久化记录；只在存储层使用，不直接返回客户端。 */
@Getter
@Setter
@TableName("data_export_job")
public class ExportJobEntity extends BaseEntity {
    private Long userId;
    private String requestKey;
    private String requestHash;
    private String scopeJson;
    private String sessionHash;
    private String securityHash;
    private String quotaMonth;
    private Integer quotaCharged;
    private String status;
    private String phase;
    private Integer attempt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime deadline;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer cancelRequested;
    private String errorCode;
    private String summaryJson;
    private String objectKey;
    private String fileName;
    private String contentType;
    private String sha256;
    private Long fileBytes;
    private Integer rowCount;
    private String artifactStatus;
    private LocalDateTime expiresAt;
    private Integer downloadCount;
}
