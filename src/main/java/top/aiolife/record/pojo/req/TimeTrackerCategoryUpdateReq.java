package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/** TimeTrackerCategoryUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeTrackerCategoryUpdateReq {

    @NotNull
    private Long id;

    /** 上级分类ID；null 表示根分类。 */
    private Long parentId;

    /** 覆盖记录是否继承公共分类的父级；仅编辑公共分类覆盖时有效。 */
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

    /**
     * 模板ID，指向被覆盖的公共分类ID
     */
    private Long templateId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean parentIdSpecified;

    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdSpecified = true;
    }

    @JsonIgnore
    public boolean isParentIdSpecified() { return parentIdSpecified; }
}
