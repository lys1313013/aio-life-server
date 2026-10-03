package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 下载任务与视频修改同事务入库；租约允许进程重启后恢复。 */
@Data
@TableName("image_import_task")
public class ImageImportTaskEntity extends BaseEntity {
    private Long videoId;
    private Long coverVersion;
    private String sourceUrl;
    private String state;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String errorCode;
}
