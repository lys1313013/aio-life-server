package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** 当前 PostgreSQL 结构；H2 只用于局部单元夹具，数据库兼容性由真实 PostgreSQL 集成测试验证。 */
public final class CurrentSchema {
    private CurrentSchema() {}

    public static String table(String name) throws IOException {
        String source = Files.readString(Path.of("sql/migrations/V20261110_01__postgresql_schema.sql"));
        var start = Pattern.compile("(?m)^CREATE TABLE \"" + Pattern.quote(name) + "\" \\(").matcher(source);
        if (!start.find()) throw new IllegalArgumentException("当前结构缺少表：" + name);
        int end = source.indexOf("\n );", start.end());
        if (end < 0) throw new IllegalStateException("表定义没有结束：" + name);
        String definition = source.substring(start.start(), end + 4);
        // 静态字段清单与 H2 夹具应用简单增删列；PostgreSQL DDL 仍由迁移集成测试验证。
        var addedColumn = Pattern.compile("(?m)^ALTER TABLE \"?" + Pattern.quote(name)
                + "\"?\\s+ADD COLUMN ([a-z_][a-z_0-9]*) ([^;]+);");
        var droppedColumn = Pattern.compile("(?m)^ALTER TABLE \\\"?" + Pattern.quote(name)
                + "\\\"?\\s+DROP COLUMN(?: IF EXISTS)? \\\"?([a-z_][a-z_0-9]*)\\\"?\\s*;");
        try (var migrations = Files.list(Path.of("sql/migrations"))) {
            for (var migration : migrations.filter(path -> path.getFileName().toString().matches("V\\d.*\\.sql"))
                    .sorted().toList()) {
                String sql = Files.readString(migration);
                var additions = addedColumn.matcher(sql);
                while (additions.find()) {
                    definition = definition.replace("\n );", ",\n  \"" + additions.group(1)
                            + "\" " + additions.group(2) + "\n );");
                }
                var matcher = droppedColumn.matcher(sql);
                while (matcher.find()) {
                    definition = definition.replaceAll("(?m)^[ \\t]*\\\"" + Pattern.quote(matcher.group(1))
                            + "\\\"[^\\n]*\\n", "");
                }
            }
        }
        return definition;
    }

    public static String h2Table(String name) throws IOException {
        return table(name)
                .replace(" COLLATE \"C\"", "")
                .replace(") STORED", ")");
    }
}
