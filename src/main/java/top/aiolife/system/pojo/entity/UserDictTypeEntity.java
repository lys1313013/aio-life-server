package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.record.pojo.entity.BaseEntity;

/** 用户自定义字典类型。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_dict_type")
public class UserDictTypeEntity extends BaseEntity {

    private Long userId;

    private Long sysDictId;

    private String dictName;

    private String dictType;

    private String icon;

    private String color;

    private String status;

    private String remark;
}
