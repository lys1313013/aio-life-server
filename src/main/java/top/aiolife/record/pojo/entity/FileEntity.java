package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.AuditEntity;
import top.aiolife.core.id.AttachmentKey;

/** 用户附件；内部雪花主键不对外暴露，接口统一使用 fileKey 的 UUIDv4 字符串。 */
@Getter
@Setter
@ToString
@TableName("attachment")
public class FileEntity extends AuditEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String fileKey;
    private Long userId;
    private Long storageObjectId;
    private Long externalResourceId;
    private String fileName;
    private String bizType;
    private Long bizId;
    private Integer isPublic;
    private Integer sortOrder;
    @TableLogic
    private Integer isDeleted;
    // 只用于接口投影，物理元数据唯一维护在 storage_object。
    @TableField(exist = false) private Long fileSize;
    @TableField(exist = false) private String fileType;
    @TableField(exist = false) private String hashValue;

    public void fillCreateCommonField(Long userId) {
        if (fileKey == null) fileKey = AttachmentKey.next();
        setUserId(userId);
        setCreateUser(userId);
        setUpdateUser(userId);
        setCreateTime(LocalDateTime.now());
        setUpdateTime(LocalDateTime.now());
        isDeleted = 0;
        sortOrder = 0;
    }
    public void fillUpdateCommonField(Long userId) {
        setUpdateUser(userId);
        setUpdateTime(LocalDateTime.now());
    }
    @Override protected Object identityKey() { return id; }
}
