package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 字典数据实体
 *
 * @author Lys
 * @date 2025/04/06 00:33
 */
@Getter
@Setter
@TableName("sys_dict_data")
public class SysDictDataEntity extends BaseEntity {

    /**
     * 所属字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典名称
     */
    @TableField(exist = false)
    private String dictName;

    /**
     * 字典类型，由 dict_type_id 联表 sys_dict_type 派生，不落库
     */
    @TableField(exist = false)
    private String dictType;

    /**
     * 字典排序
     */
    private Integer sortOrder;

    /**
     * 字典标签
     */
    private String dictLabel;

    /**
     * 字典值
     */
    private String dictValue;

    /**
     * CSS类名
     */
    private String cssClass;

    /**
     * 列表类名
     */
    private String listClass;

    /**
     * 是否默认值：1=是，0=否
     */
    private Integer isDefault;

    /**
     * 状态
     */
    private Integer isEnabled;

    /**
     * 备注
     */
    private String remark;
}