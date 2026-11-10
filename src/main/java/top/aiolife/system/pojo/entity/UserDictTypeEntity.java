package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 用户自定义字典类型。 */
@Getter
@Setter
@ToString
@TableName("user_dict_type")
public class UserDictTypeEntity extends BaseEntity {

    private Long userId;

    private Long sysDictId;

    private String dictName;

    private String dictType;

    private String icon;

    private String color;

    private Integer isEnabled;

    private String remark;
}
