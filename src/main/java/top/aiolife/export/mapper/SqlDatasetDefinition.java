package top.aiolife.export.mapper;

import top.aiolife.export.dataset.ExportColumn;

import java.util.List;

/** 仅从随应用发布的白名单读取，绝不从 HTTP 参数反序列化。 */
public record SqlDatasetDefinition(
        String key,
        String title,
        String table,
        String ownerColumn,
        String bizType,
        List<String> menus,
        List<ParentScope> parents,
        List<ExportColumn> columns) {
    public SqlDatasetDefinition {
        identifier(key);
        identifier(table);
        if (ownerColumn != null) identifier(ownerColumn);
        menus = List.copyOf(menus);
        parents = List.copyOf(parents);
        columns = List.copyOf(columns);
        if (ownerColumn == null && parents.isEmpty())
            throw new IllegalArgumentException("Missing ownership rule");
        if (columns.stream().noneMatch(c -> c.key().equals("id")))
            throw new IllegalArgumentException("Missing cursor");
    }

    public static String identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid SQL identifier");
        return "`" + value + "`";
    }

    public record ParentScope(
            String table, String localColumn, String targetColumn, String ownerColumn) {
        public ParentScope {
            identifier(table);
            identifier(localColumn);
            identifier(targetColumn);
            identifier(ownerColumn);
        }
    }
}
