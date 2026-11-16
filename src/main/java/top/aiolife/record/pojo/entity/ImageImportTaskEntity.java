package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 按目标位置等待来源完成；不领取下载租约，执行器可反复扫描推进。 */
@Getter
@Setter
@TableName("import_job")
public class ImageImportTaskEntity extends BaseEntity {
    private String bizType;
    private Long bizId;
    private Long externalResourceId;
    private String state;
    private LocalDateTime nextAttemptAt;
    private String errorCode;
    private String resultAttachmentId;
}
