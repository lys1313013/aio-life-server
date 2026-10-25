package top.aiolife.export.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

import top.aiolife.core.pojo.entity.BaseEntity;

import java.time.LocalDateTime;

/** 导出持久化记录；只在存储层使用，不直接返回客户端。 */
@Getter
@Setter
@TableName("data_export_grant")
public class ExportGrantEntity extends BaseEntity {
    private Long userId;
    private String tokenHash;
    private String sessionHash;
    private String securityHash;
    private String scopeHash;
    private String purpose;
    private Long jobId;
    private LocalDateTime expiresAt;
    private Integer consumed;
}
