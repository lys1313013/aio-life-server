package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 时间追踪-分类配置表(TimeTrackerCategory)表实体类
 *
 * @author Lys1313013
 * @since 2026-02-16 18:48:22
 */
@Data
@TableName("time_tracker_category")
public class TimeTrackerCategoryEntity extends BaseEntity {
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 模板ID，指向被覆盖的公共分类ID
     */
    private Long templateId;
    /** 上级分类：0 为一级；覆盖记录 null 表示继承公共分类。 */
    private Long parentId;

    @TableField(exist = false)
    @JsonIgnore
    private boolean parentIdSpecified;

    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdSpecified = true;
    }

    /**
     * 分类名称
     */
    private String name;
    /**
     * 颜色值(Hex)
     */
    private String color;
    /**
     * 图标名称(Iconify格式)
     */
    private String icon;
    /**
     * 描述
     */
    private String description;
    /**
     * 是否记录时间
     */
    private Integer isTrackTime;
    /**
     * 排序权重
     */
    private Integer sort;
    /**
     * 是否启用：1-启用，0-禁用
     */
    private Integer isEnabled;
    /**
     * 是否删除
     */
    private Integer isDeleted;
    /**
     * 时间类型：1-必须时间，2-积极时间，3-消极时间
     */
    private Integer timeType;
}
