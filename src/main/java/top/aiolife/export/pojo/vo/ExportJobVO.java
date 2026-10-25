package top.aiolife.export.pojo.vo;

import java.time.LocalDateTime;

/** 明确响应字段，内部对象键、会话摘要和授权信息不出存储层。 */
public record ExportJobVO(
        String id,
        String status,
        String phase,
        String errorCode,
        int rowCount,
        String fileBytes,
        String fileName,
        String sha256,
        String artifactStatus,
        LocalDateTime createTime,
        LocalDateTime finishedAt,
        LocalDateTime expiresAt) {}
