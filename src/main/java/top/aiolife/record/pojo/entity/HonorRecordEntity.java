package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 荣誉记录实体
 *
 * @author Lys
 * @date 2026/04/11
 */
@Data
@TableName("honor_record")
public class HonorRecordEntity extends BaseEntity {

    private Long userId;

    private String title;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate honorDate;

    private String issuer;

    private String level;

    private Long categoryId;

    private String customCategory;

    private String tags;

    @TableField(exist = false)
    private java.util.List<String> fileIds;

    @TableField(exist = false)
    private java.util.List<top.aiolife.record.pojo.vo.FileVO> files;

    private Integer isTop;

    private Integer isPublic;

    private Integer sortOrder;

    public void fillCreateCommonField(Long userId) {
        this.setCreateUser(userId);
        this.setUpdateUser(userId);
        this.setCreateTime(java.time.LocalDateTime.now());
        this.setUpdateTime(java.time.LocalDateTime.now());
        this.setIsDeleted(0);
    }

    public void fillUpdateCommonField(Long userId) {
        this.setUpdateUser(userId);
        this.setUpdateTime(java.time.LocalDateTime.now());
    }
}
