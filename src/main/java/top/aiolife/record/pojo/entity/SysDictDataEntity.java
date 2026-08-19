package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.AuditEntity;

/**
 * 字典数据实体
 *
 * @author Lys
 * @date 2025/04/06 00:33
 */
@Getter
@Setter
@TableName("sys_dict_data")
public class SysDictDataEntity extends AuditEntity {

    /**
     * 字典代码，作为主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long dictCode;
    /**
     * 字典ID
     */
    private Long dictId;

    /**
     * 字典名称
     */
    @TableField(exist = false)
    private String dictName;

    /**
     * 字典类型
     */
    @TableField(exist = false)
    private String dictType;

    /**
     * 字典排序
     */
    private Integer dictSort;

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
     * 是否为默认值
     */
    private String isDefault;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private Integer isDeleted;
}
