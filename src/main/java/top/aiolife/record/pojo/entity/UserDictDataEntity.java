package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 用户字典数据实体类
 *
 * @author Lys
 */
@Getter
@Setter
@ToString
@TableName("user_dict_data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictDataEntity extends BaseEntity {

    /**
     * 所属用户ID
     */
    private Long userId;

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
     * 字典项的补充属性（JSON 字符串）
     */
    private String extraData;

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
