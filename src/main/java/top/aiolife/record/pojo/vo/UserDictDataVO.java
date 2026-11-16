package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** UserDictDataVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictDataVO {

    /** 是否为系统公共预设；预设不可删除，只能通过个人覆盖停用或改名。 */
    private Boolean preset;

    private Long id;

    /**
     * 模板ID，指向被覆盖的公共字典ID
     */
    private Long templateId;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 字典排序
     */
    private Integer sortOrder;

    /**
     * 字典标签(分类名称)
     */
    private String dictLabel;

    /**
     * 字典键值(分类标识)
     */
    private String dictValue;

    /**
     * 颜色值(Hex)
     */
    private String color;

    /**
     * 图标名称(Iconify格式)
     */
    private String icon;

    /**
     * 特定分类所需的额外扩展字段(JSON)
     */
    private String extData;

    /**
     * 是否默认值：1=是，0=否
     */
    private Integer isDefault;

    /**
     * 是否启用：1=启用，0=停用
     */
    private Integer isEnabled;

    /**
     * 是否只读：1=是，0=否；用于限制普通用户修改公共分类
     */
    private Integer isReadonly;

    /**
     * 备注
     */
    private String remark;
}
