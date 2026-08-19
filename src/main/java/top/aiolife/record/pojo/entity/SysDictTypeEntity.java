package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.AuditEntity;

/**
 * 字典类型实体
 *
 * @author Lys
 * @date 2025/04/06 00:34
 */
@Getter
@Setter
@TableName("sys_dict_type")
public class SysDictTypeEntity extends AuditEntity {

    /**
     * 字典类型的唯一标识
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long dictId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型
     */
    private String dictType;

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
