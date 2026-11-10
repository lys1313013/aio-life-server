package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.pojo.vo.FileVO;

/**
 * 荣誉记录实体
 *
 * @author Lys
 * @date 2026/04/11
 */
@Getter
@Setter
@ToString
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
    private List<String> fileIds;

    @TableField(exist = false)
    private List<FileVO> files;

    private Integer isTop;

    private Integer isPublic;

    private Integer sortOrder;

    public void fillCreateCommonField(Long userId) {
        this.setCreateUser(userId);
        this.setUpdateUser(userId);
        this.setCreateTime(LocalDateTime.now());
        this.setUpdateTime(LocalDateTime.now());
        this.setIsDeleted(0);
    }

    public void fillUpdateCommonField(Long userId) {
        this.setUpdateUser(userId);
        this.setUpdateTime(LocalDateTime.now());
    }
}
