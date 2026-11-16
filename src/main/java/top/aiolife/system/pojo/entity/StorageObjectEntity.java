package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 不可变物理字节；权限与用户归属由附件持有。回收使用 Mapper 物理删除。 */
@Getter
@Setter
@ToString
@TableName("storage_object")
public class StorageObjectEntity extends BaseEntity {
    private String bucket;
    private String objectKey;
    private String contentHash;
    private Long fileSize;
    private String contentType;
    private Integer imageWidth;
    private Integer imageHeight;
}
