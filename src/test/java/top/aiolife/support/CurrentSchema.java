package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/** 当前 PostgreSQL 结构；H2 只用于局部单元夹具，数据库兼容性由真实 PostgreSQL 集成测试验证。 */
public final class CurrentSchema {
    /** 基线之后增量改名：基线名 → 当前物理名。 */
    private static final Map<String, String> CURRENT_NAMES = Map.of("user", "sys_user");
    /** 基线之后增量删除的表，不再属于当前结构。 */
    private static final Map<String, String> DROPPED_TABLES = Map.of(
            "record_article", "V20261112_04",
            "record_article_annotation", "V20261112_04",
            "enum_type", "V20261112_04",
            "user_dict_type", "V20261112_04");
    /** 基线之后增量删除的列。 */
    private static final Map<String, java.util.Set<String>> DROPPED_COLUMNS = Map.of(
            "device", java.util.Set.of("purchase_company"),
            "time_tracker_category", java.util.Set.of("code"),
            "honor_record", java.util.Set.of("file_id"));
    /** 基线之后增量改名的列：当前表名 → 基线列名到当前列名。 */
    private static final Map<String, Map<String, String>> RENAMED_COLUMNS = Map.of(
            "time_record", Map.of("start_time", "start_minute", "end_time", "end_minute", "duration", "duration_minutes"),
            "b_video", Map.of("duration", "duration_seconds", "watched_duration", "watched_duration_seconds"),
            "performance", Map.of("duration", "duration_minutes"));

    private CurrentSchema() {}

    public static boolean dropped(String baselineName) {
        return DROPPED_TABLES.containsKey(baselineName);
    }

    public static boolean droppedColumn(String table, String column) {
        return DROPPED_COLUMNS.getOrDefault(table, java.util.Set.of()).contains(column);
    }

    public static String currentName(String baselineName) {
        return CURRENT_NAMES.getOrDefault(baselineName, baselineName);
    }

    public static String table(String name) throws IOException {
        String source = Files.readString(Path.of("sql/migrations/V20261110_01__postgresql_schema.sql"));
        String baselineName = CURRENT_NAMES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(name))
                .map(Map.Entry::getKey)
                .findFirst().orElse(name);
        var start = Pattern.compile("(?m)^CREATE TABLE \"" + Pattern.quote(baselineName) + "\" \\(").matcher(source);
        if (!start.find()) throw new IllegalArgumentException("当前结构缺少表：" + name);
        int end = source.indexOf("\n );", start.end());
        if (end < 0) throw new IllegalStateException("表定义没有结束：" + name);
        String definition = source.substring(start.start(), end + 4);
        if (!baselineName.equals(name)) {
            definition = definition.replace("CREATE TABLE \"" + baselineName + "\"", "CREATE TABLE \"" + name + "\"");
        }
        for (var rename : RENAMED_COLUMNS.getOrDefault(name, Map.of()).entrySet()) {
            definition = definition.replace("\"" + rename.getKey() + "\"", "\"" + rename.getValue() + "\"");
        }
        return definition;
    }
    public static String h2Table(String name) throws IOException {
        return table(name).replace(" COLLATE aio_unicode_ci", "").replace(") STORED", ")");
    }
}
