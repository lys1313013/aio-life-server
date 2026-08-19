package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 枚举类型定义。 */
@Data
@TableName("enum_type")
public class EnumTypeEntity {

    @TableId(value = "type_id", type = IdType.AUTO)
    private Long typeId;

    private String typeName;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
