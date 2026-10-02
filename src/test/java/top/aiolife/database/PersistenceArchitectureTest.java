package top.aiolife.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止业务代码绕过 Mapper；测试夹具和数据库结构检查不在生产源码扫描范围内。 */
class PersistenceArchitectureTest {
    private static final Path SOURCE = Path.of("src/main/java/top/aiolife");
    private static final Pattern DIRECT_JDBC = Pattern.compile(
            "\\b(?:JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient|JdbcOperations|NamedParameterJdbcOperations)\\b"
                    + "|\\bjava\\.sql\\.(?:Connection|Statement|PreparedStatement|DriverManager)\\b");
    private static final Pattern SQL = Pattern.compile(
            "\\b(?:SELECT\\s+.+?\\s+FROM|INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void 生产代码禁止绕过MyBatis直接访问数据库() throws Exception {
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(SOURCE)) {
            var files = paths.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(files.isEmpty(), "必须扫描实际生产源码");
            for (var file : files) {
                if (DIRECT_JDBC.matcher(Files.readString(file)).find()) violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "业务数据库访问必须使用 Mapper：" + violations);
    }

    @Test
    void 业务SQL必须放入Mapper() throws Exception {
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(SOURCE)) {
            for (var file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.toString().contains("/mapper/")) continue;
                if (SQL.matcher(Files.readString(file)).find()) violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "将 SQL 移至 Mapper，Service / Guard 只编排业务：" + violations);
    }
}
