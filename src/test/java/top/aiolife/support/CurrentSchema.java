package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 测试使用全量基线与未合并的字段迁移，迁移计划只维护在 SQL 中。 */
public final class CurrentSchema {
    private static final Path SOURCE = Path.of("sql/migrations/B20261028_00__baseline.sql");
    private static final Path ASSET_MIGRATION =
            Path.of("sql/migrations/V20261028_01__asset_purchase_source.sql");
    private static final String SQL_STRING = "'((?:''|[^'])*)'";
    private static final String NULLABLE_SQL_STRING = "(NULL|'(?:''|[^'])*')";
    private static final Pattern ASSET_COLUMN = Pattern.compile(
            "\\(\\s*" + SQL_STRING + ",\\s*\\d+,\\s*" + SQL_STRING + ",\\s*"
                    + SQL_STRING + ",\\s*" + NULLABLE_SQL_STRING + ",\\s*"
                    + NULLABLE_SQL_STRING + ",\\s*" + SQL_STRING + ",\\s*"
                    + SQL_STRING + "\\s*\\)");

    private CurrentSchema() {}

    public static String table(String name) throws IOException {
        String schema = Files.readString(SOURCE);
        var table = Pattern.compile("(?im)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`"
                + Pattern.quote(name) + "`\\s*\\(").matcher(schema);
        if (!table.find()) throw new IllegalArgumentException("当前结构缺少表：" + name);
        int start = table.start();
        String sql = schema.substring(start, statementEnd(schema, start));
        sql = applyAssetColumns(name, sql);
        String migration = Files.readString(Path.of("sql/migrations/V20261028_02__unify_common_fields.sql"));
        var changes = Pattern.compile("CALL aio_unify_field\\('([^']+)', '([^']+)', '([^']+)', '([^']+)', ([01]), 0\\)").matcher(migration);
        while (changes.find()) {
            if (!name.equals(changes.group(1))) continue;
            sql = sql.replace("`" + changes.group(2) + "`", "`" + changes.group(3) + "`");
            if (changes.group(5).equals("1")) {
                sql = sql.replaceAll("(?m)^\\s*`is_enabled`[^\\n]*", "  `is_enabled` TINYINT DEFAULT 1 COMMENT '" + changes.group(4) + "',");
            }
        }
        return sql;
    }

    /** 01 的列计划是新增来源字段的唯一定义，测试不得退回只含三个字段的全量基线。 */
    private static String applyAssetColumns(String name, String sql) throws IOException {
        String migration = Files.readString(ASSET_MIGRATION);
        var planStart = Pattern.compile("INSERT\\s+INTO\\s+`?_aio_asset_columns`?\\s*\\(",
                Pattern.CASE_INSENSITIVE).matcher(migration);
        if (!planStart.find()) throw new IllegalStateException("采集来源迁移缺少列计划");
        String plan = migration.substring(planStart.start(), statementEnd(migration, planStart.start()));
        var columns = ASSET_COLUMN.matcher(plan);
        boolean foundPlan = false;
        while (columns.find()) {
            foundPlan = true;
            if (!name.equals(unescape(columns.group(1)))) continue;
            String column = unescape(columns.group(2));
            String type = unescape(columns.group(3));
            String charset = nullableString(columns.group(4));
            String collation = nullableString(columns.group(5));
            String comment = unescape(columns.group(6));
            String after = unescape(columns.group(7));
            String definition = "  `" + column + "` " + type
                    + (charset == null ? "" : " CHARACTER SET " + charset)
                    + (collation == null ? "" : " COLLATE " + collation)
                    + " DEFAULT NULL COMMENT '" + comment.replace("'", "''") + "',";
            var existing = Pattern.compile("(?m)^\\s*`" + Pattern.quote(column) + "`[^\\n]*")
                    .matcher(sql);
            if (existing.find()) {
                sql = existing.replaceFirst(Matcher.quoteReplacement(definition));
            } else {
                var predecessor = Pattern.compile("(?m)^(\\s*`" + Pattern.quote(after)
                        + "`[^\\n]*\\n)").matcher(sql);
                if (!predecessor.find()) {
                    throw new IllegalStateException("采集来源字段缺少前置列：" + name + "." + after);
                }
                sql = predecessor.replaceFirst(Matcher.quoteReplacement(
                        predecessor.group(1) + definition + "\n"));
            }
        }
        if (!foundPlan) throw new IllegalStateException("采集来源列计划无法解析");
        return sql;
    }

    private static String nullableString(String value) {
        return value.equalsIgnoreCase("NULL") ? null : unescape(value.substring(1, value.length() - 1));
    }

    private static String unescape(String value) {
        return value.replace("''", "'");
    }

    /** 忽略 COMMENT 字符串中的分号，只在 SQL 字符串外结束当前语句。 */
    private static int statementEnd(String source, int start) {
        boolean quoted = false;
        for (int i = start; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '\'') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '\'') i++;
                else quoted = !quoted;
            } else if (current == ';' && !quoted) {
                return i;
            }
        }
        throw new IllegalStateException("SQL 语句缺少结束符");
    }

    /** H2 的 MySQL 模式保留字段、索引及约束，仅移除引擎和排序规则等表选项。 */
    public static String h2Table(String name) throws IOException {
        String sql = table(name);
        return sql.substring(0, sql.indexOf(" ENGINE="));
    }
}
