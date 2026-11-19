package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.system.mapper.SchemaJsonTypeHandler;

@Getter
@Setter
@TableName(value = "query_schema_relation", autoResultMap = true)
public class QuerySchemaRelationEntity extends BaseEntity {
    private String sourceTable;
    private String targetTable;
    private String relationKey;
    private String displayName;
    private String relationKind;
    private String cardinality;

    @TableField(typeHandler = SchemaJsonTypeHandler.class)
    private String columnMapping;

    @TableField(typeHandler = SchemaJsonTypeHandler.class)
    private String conditions;

    private String description;
}
