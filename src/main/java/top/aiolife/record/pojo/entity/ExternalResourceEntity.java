package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 成功来源持续复用，无刷新和过期状态。 */
@Getter
@Setter
@TableName("external_resource")
public class ExternalResourceEntity extends BaseEntity {
    private String platform;
    private String resourceType;
    private String externalKey;
    private String variant;
    private String sourceUrl;
    private Long storageObjectId;
    private String state;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String errorCode;
    private LocalDateTime fetchedAt;
}
