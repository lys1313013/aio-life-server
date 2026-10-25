package top.aiolife.export.mapper;

import static top.aiolife.export.mapper.SqlDatasetDefinition.identifier;

import java.util.Map;
import java.util.stream.Collectors;

/** 所有动态标识符来自已校验的发布白名单，所有运行时值使用绑定参数。 */
public final class ExportDatasetSql {
    private ExportDatasetSql() {}

    public static String select(Map<String, Object> parameters) {
        var definition = (SqlDatasetDefinition) parameters.get("definition");
        String projection =
                definition.columns().stream()
                        .map(
                                column ->
                                        "d."
                                                + identifier(column.key())
                                                + " AS "
                                                + identifier(column.key()))
                        .collect(Collectors.joining(", "));
        StringBuilder sql =
                new StringBuilder("SELECT ")
                        .append(projection)
                        .append(" FROM ")
                        .append(identifier(definition.table()))
                        .append(" d WHERE d.is_deleted = 0");
        if (definition.ownerColumn() != null)
            sql.append(" AND d.")
                    .append(identifier(definition.ownerColumn()))
                    .append(" = #{ownerId}");
        if (definition.bizType() != null) sql.append(" AND d.biz_type = #{definition.bizType}");
        for (var parent : definition.parents()) {
            sql.append(" AND EXISTS (SELECT 1 FROM ")
                    .append(identifier(parent.table()))
                    .append(" p WHERE p.")
                    .append(identifier(parent.targetColumn()))
                    .append(" = d.")
                    .append(identifier(parent.localColumn()))
                    .append(" AND p.")
                    .append(identifier(parent.ownerColumn()))
                    .append(" = #{ownerId} AND p.is_deleted = 0)");
        }
        if (parameters.get("cursor") != null) sql.append(" AND d.id > #{cursor}");
        return sql.append(" ORDER BY d.id ASC LIMIT #{limit}").toString();
    }
}
