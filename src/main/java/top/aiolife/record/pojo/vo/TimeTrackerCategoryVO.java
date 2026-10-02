package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** TimeTrackerCategoryVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeTrackerCategoryVO {
    private Boolean isHidden;

    private Long userId;

    private Long id;

    /**
     * 模板ID，指向被覆盖的公共分类ID
     */
    private Long templateId;

    /** 上级分类：0 为一级；覆盖记录 null 表示继承公共分类。 */
    private Long parentId;

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
     * 时间类型：1-必须时间，2-积极时间，3-消极时间
     */
    private Integer timeType;
}
