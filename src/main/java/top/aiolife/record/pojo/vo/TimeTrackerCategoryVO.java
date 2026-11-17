package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** TimeTrackerCategoryVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeTrackerCategoryVO {
    private Boolean isHidden;

    /** 是否为系统公共预设；预设不可直接删除，只能通过个人覆盖停用或改名。 */
    private Boolean preset;

    private Long id;

    /**
     * 模板ID，指向被覆盖的公共分类ID
     */
    private Long templateId;

    /** 上级分类ID；null 表示根分类。 */
    private Long parentId;

    /** 覆盖记录是否继承公共分类的父级；非覆盖记录为空。 */
    private Boolean inheritParent;

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
    private Integer sortOrder;

    /**
     * 是否启用：1-启用，0-禁用
     */
    private Integer isEnabled;

}
