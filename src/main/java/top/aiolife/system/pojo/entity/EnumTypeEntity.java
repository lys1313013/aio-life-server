package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

/** 枚举类型定义。 */
@Getter
@Setter
@ToString
@TableName("enum_type")
public class EnumTypeEntity extends IdentityEntity {

    @TableId(value = "type_id", type = IdType.AUTO)
    private Long typeId;

    private String typeName;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    protected Object identityKey() {
        return typeId;
    }
}
