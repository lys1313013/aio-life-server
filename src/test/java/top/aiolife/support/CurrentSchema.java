package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** 当前 PostgreSQL 结构；H2 只用于局部单元夹具，数据库兼容性由真实 PostgreSQL 集成测试验证。 */
public final class CurrentSchema {
    private CurrentSchema() {}

    private static final Path BASELINE = Path.of("sql/migrations/V20261110_01__postgresql_schema.sql");
    private static final Pattern TABLE_RENAME = Pattern.compile(
            "(?m)^[ \\t]*ALTER TABLE \"?([a-z_][a-z_0-9]*)\"?\\s+RENAME TO \"?([a-z_][a-z_0-9]*)\"?\\s*;");
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?:^|,\\s*)ADD COLUMN\\s+([a-z_][a-z_0-9]*)\\s+(.+?)(?=,\\s*ADD COLUMN\\s|$)",
            Pattern.DOTALL);

    public static String table(String name) throws IOException {
        List<String> migrations = migrationSources();
        String original = baselineTableName(name, migrations);
        String current = original;
        String source = Files.readString(BASELINE);
        var start = Pattern.compile("(?m)^CREATE TABLE \"" + Pattern.quote(current) + "\" \\(").matcher(source);
        if (!start.find()) throw new IllegalArgumentException("当前结构缺少表：" + name);
        int end = source.indexOf("\n );", start.end());
        if (end < 0) throw new IllegalStateException("表定义没有结束：" + name);
        String definition = source.substring(start.start(), end + 4);
        // 静态字段清单与 H2 夹具应用简单增删列和表改名；PostgreSQL DDL 仍由迁移集成测试验证。
        for (String sql : migrations) {
            var addColumns = Pattern.compile("(?m)^[ \\t]*ALTER TABLE \"?" + Pattern.quote(current)
                    + "\"?\\s+(ADD COLUMN\\s+[^;]+);").matcher(sql);
            while (addColumns.find()) {
                var additions = ADD_COLUMN.matcher(addColumns.group(1));
                while (additions.find()) {
                    definition = definition.replace("\n );", ",\n  \"" + additions.group(1)
                            + "\" " + additions.group(2).stripTrailing() + "\n );");
                }
            }
            var droppedColumn = Pattern.compile("(?m)^[ \\t]*ALTER TABLE \\\"?" + Pattern.quote(current)
                    + "\\\"?\\s+DROP COLUMN(?: IF EXISTS)? \\\"?([a-z_][a-z_0-9]*)\\\"?\\s*;");
            var matcher = droppedColumn.matcher(sql);
            while (matcher.find()) {
                definition = definition.replaceAll("(?m)^[ \\t]*\\\"" + Pattern.quote(matcher.group(1))
                        + "\\\"[^\\n]*\\n", "");
            }
            var renamedColumn = Pattern.compile("(?m)^[ \\t]*ALTER TABLE \\\"?" + Pattern.quote(current)
                    + "\\\"?\\s+RENAME COLUMN \\\"?([a-z_][a-z_0-9]*)\\\"?\\s+TO \\\"?([a-z_][a-z_0-9]*)\\\"?\\s*;");
            var renames = renamedColumn.matcher(sql);
            while (renames.find()) {
                definition = definition.replaceAll("(?m)^([ \\t]*)\\\"" + Pattern.quote(renames.group(1))
                        + "\\\"", "$1\"" + renames.group(2) + "\"");
                definition = definition.replace("PRIMARY KEY (\"" + renames.group(1) + "\")",
                        "PRIMARY KEY (\"" + renames.group(2) + "\")");
            }
            current = renameTable(sql, current);
        }
        if (!name.equals(original)) {
            definition = definition.replaceFirst(
                    "(?m)^CREATE TABLE \"" + Pattern.quote(original) + "\" \\(",
                    "CREATE TABLE \"" + Matcher.quoteReplacement(name) + "\" (");
        }
        return definition;
    }

    private static String baselineTableName(String name, List<String> migrations) {
        String current = name;
        for (int i = migrations.size() - 1; i >= 0; i--) {
            var renames = TABLE_RENAME.matcher(migrations.get(i));
            while (renames.find()) {
                if (renames.group(2).equals(current)) current = renames.group(1);
            }
        }
        return current;
    }

    private static String renameTable(String sql, String current) {
        String renamed = current;
        var renames = TABLE_RENAME.matcher(sql);
        while (renames.find()) {
            if (renames.group(1).equals(renamed)) renamed = renames.group(2);
        }
        return renamed;
    }

    private static List<Path> migrationPaths() throws IOException {
        try (var migrations = Files.list(Path.of("sql/migrations"))) {
            return migrations.filter(path -> path.getFileName().toString().matches("V\\d.*\\.sql"))
                    .sorted().toList();
        }
    }

    private static List<String> migrationSources() throws IOException {
        List<Path> paths = migrationPaths();
        List<String> sources = new ArrayList<>(paths.size());
        for (Path path : paths) sources.add(Files.readString(path));
        return sources;
    }

    /** 给自行准备数据的 PostgreSQL 业务测试初始化结构，依赖地区目录的高校名录也在完整迁移测试中执行。 */
    public static void initializePostgresFixture(JdbcTemplate jdbc) throws IOException {
        for (Path path : migrationPaths()) {
            String name = path.getFileName().toString();
            if (name.startsWith("V20261110_02_") || name.equals("V20261119_04__seed_universities.sql")) continue;
            jdbc.execute(Files.readString(path));
        }
    }

    public static String h2Table(String name) throws IOException {
        return table(name)
                .replace(" COLLATE \"C\"", "")
                .replace(") STORED", ")");
    }
}
