package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 不可变图片字节；用户权限由 file 引用持有。 */
@Data
@TableName("storage_object")
public class StorageObjectEntity extends BaseEntity {
    private String bucket;
    private String objectKey;
    private String sha256;
    private Long fileSize;
    private String contentType;
}
