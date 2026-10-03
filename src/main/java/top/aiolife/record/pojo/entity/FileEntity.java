package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file")
public class FileEntity {
    private Long storageObjectId;


    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private Long createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private Long updateUser;

    @TableLogic
    private Integer isDeleted;

    public void fillCreateCommonField(Long userId) {
        this.createUser = userId;
        this.updateUser = userId;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.isDeleted = 0;
    }

    public void fillUpdateCommonField(Long userId) {
        this.updateUser = userId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 文件原名/MinIO对象名
     */
    private String fileName;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件MIME类型
     */
    private String fileType;

    /**
     * 文件哈希值
     */
    private String hashValue;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务记录ID
     */
    private Long bizId;

    /**
     * 是否公开：0-否，1-是
     */
    private Integer isPublic;
}
