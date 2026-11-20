package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL 基线提供测试夹具的建表定义，业务测试自行准备所需数据。 */
public final class CurrentSchema {
    private CurrentSchema() {}

    private static final Path MIGRATIONS = Path.of("sql/migrations");
    private static final Path BASELINE = MIGRATIONS.resolve("V20261110_01__postgresql_schema.sql");
    private static final String CONSOLIDATED_THROUGH = "20261120_01";

    public static String baselineDdl() throws IOException {
        return Files.readString(BASELINE);
    }

    public static String table(String name) throws IOException {
        var table = Pattern.compile("(?ms)^CREATE TABLE \"" + Pattern.quote(name) + "\" \\(\\n.*?^\\);")
                .matcher(baselineDdl());
        if (!table.find()) throw new IllegalArgumentException("基线缺少表：" + name);
        return table.group();
    }

    public static void initializePostgresFixture(JdbcTemplate jdbc) throws IOException {
        jdbc.execute(baselineDdl());
        try (var paths = Files.list(MIGRATIONS)) {
            for (var path : paths.filter(file -> file.getFileName().toString().matches("V\\d.*\\.sql")).sorted().toList()) {
                String filename = path.getFileName().toString();
                String version = filename.substring(1, filename.indexOf("__"));
                if (version.compareTo(CONSOLIDATED_THROUGH) > 0) {
                    jdbc.execute(Files.readString(path));
                }
            }
        }
    }

    public static String h2Table(String name) throws IOException {
        return table(name)
                .replace(" COLLATE \"pg_catalog\".\"C\"", "")
                .replace(" COLLATE \"C\"", "")
                .replace(") STORED", ")");
    }
}
