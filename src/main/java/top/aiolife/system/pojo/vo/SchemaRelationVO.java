package top.aiolife.system.pojo.vo;

import java.util.List;
import top.aiolife.system.pojo.req.SchemaColumnMapping;
import top.aiolife.system.pojo.req.SchemaRelationCondition;

public record SchemaRelationVO(
        Long id, String sourceTable, String targetTable, String relationKey, String displayName,
        String relationKind, String cardinality, List<SchemaColumnMapping> columnMapping,
        List<SchemaRelationCondition> conditions, String description, boolean valid, String invalidReason
) {}
