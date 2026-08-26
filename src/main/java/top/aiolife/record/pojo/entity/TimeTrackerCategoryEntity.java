package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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

