package top.aiolife.export.mapper;

import static top.aiolife.export.mapper.SqlDatasetDefinition.identifier;

import java.util.Map;
import java.util.stream.Collectors;

/** 所有动态标识符来自已校验的发布白名单，所有运行时值使用绑定参数。 */
public final class ExportDatasetSql {
    private ExportDatasetSql() {}

    public static String select(Map<String, Object> parameters) {
        var definition = (SqlDatasetDefinition) parameters.get("definition");
        boolean attachment = definition.table().equals("attachment");
        String projection =
                definition.columns().stream()
                        .map(
                                column ->
                                        (attachment ? switch (column.key()) {
                                            case "id" -> "d.file_key";
                                            case "file_size" -> "o.file_size";
                                            case "file_type" -> "o.content_type";
                                            default -> "d." + identifier(column.key());
                                        } : "d."
                                                + identifier(column.key())
                                        )
                                                + " AS "
                                                + identifier(column.key()))
                        .collect(Collectors.joining(", "));
        StringBuilder sql =
                new StringBuilder("SELECT ")
                        .append(projection)
                        .append(" FROM ")
                        .append(identifier(definition.table()))
                        .append(attachment ? " d JOIN storage_object o ON o.id=d.storage_object_id" : " d")
                        .append(" WHERE d.is_deleted = 0");
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
        String cursorColumn = attachment ? "d.file_key" : "d.id";
        if (parameters.get("cursor") != null) sql.append(" AND ").append(cursorColumn).append(" > #{cursor}");
        return sql.append(" ORDER BY ").append(cursorColumn).append(" ASC LIMIT #{limit}").toString();
    }
}
