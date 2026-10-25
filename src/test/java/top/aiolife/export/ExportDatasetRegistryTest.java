package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import top.aiolife.export.mapper.ExportDatasetSql;
import top.aiolife.export.mapper.SqlDatasetDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class ExportDatasetRegistryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void 所有物理表必须明确登记导出或排除且秘密字段不会自动暴露() throws Exception {
        List<SqlDatasetDefinition> datasets =
                json.readValue(
                        Files.readString(Path.of("src/main/resources/export/datasets.json")),
                        new TypeReference<>() {});
        Map<String, String> excluded =
                json.readValue(
                        Files.readString(Path.of("src/main/resources/export/excluded-tables.json")),
                        new TypeReference<>() {});
        Set<String> classified = new HashSet<>(excluded.keySet());
        for (var item : datasets) {
            assertTrue(classified.add(item.table()), "Duplicate classification: " + item.table());
            assertNotNull(
                    item.columns().stream()
                            .filter(c -> c.key().equals("id"))
                            .findFirst()
                            .orElse(null));
            if (item.key().equals("feedback_comment")) {
                assertFalse(
                        item.columns().stream().anyMatch(c -> c.key().equals("user_id")),
                        "Do not export other reply authors' internal identifiers");
            }
            for (var column : item.columns())
                assertFalse(
                        Set.of(
                                        "password",
                                        "password_salt",
                                        "secondary_password",
                                        "secondary_password_salt",
                                        "access_token",
                                        "api_key",
                                        "salt",
                                        "card_no_ciphertext",
                                        "card_no_fingerprint",
                                        "app_secret_ciphertext",
                                        "meta_fields")
                                .contains(column.key()),
                        item.key() + "." + column.key());
        }
        var matcher =
                Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `([^`]+)`")
                        .matcher(
                                Files.readString(
                                        Path.of(
                                                "sql/1_init_table/2026-08-18_init_all_tables.sql")));
        while (matcher.find())
            assertTrue(
                    classified.contains(matcher.group(1)),
                    "Missing classification: " + matcher.group(1));
    }

    @Test
    void 每个业务字段都必须显式导出或说明排除原因() throws Exception {
        List<SqlDatasetDefinition> datasets =
                json.readValue(
                        Files.readString(Path.of("src/main/resources/export/datasets.json")),
                        new TypeReference<>() {});
        Map<String, Map<String, String>> excluded =
                json.readValue(
                        Files.readString(
                                Path.of("src/main/resources/export/excluded-columns.json")),
                        new TypeReference<>() {});
        String schema =
                Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        for (var dataset : datasets) {
            var table =
                    Pattern.compile(
                                    "CREATE TABLE(?: IF NOT EXISTS)? `"
                                            + dataset.table()
                                            + "`\\s*\\((.*?)\\) ENGINE",
                                    Pattern.DOTALL)
                            .matcher(schema);
            assertTrue(table.find(), dataset.table());
            Set<String> actual = new HashSet<>();
            var fields = Pattern.compile("(?m)^\\s*`([^`]+)`\\s+").matcher(table.group(1));
            while (fields.find()) actual.add(fields.group(1));
            Set<String> classified = new HashSet<>();
            dataset.columns().forEach(column -> classified.add(column.key()));
            var omissions = excluded.getOrDefault(dataset.table(), Map.of());
            omissions.forEach(
                    (key, reason) -> {
                        assertTrue(
                                classified.add(key),
                                "Duplicate field classification: " + dataset.table() + "." + key);
                        assertFalse(reason.isBlank());
                    });
            assertEquals(actual, classified, "Unclassified or obsolete fields: " + dataset.table());
        }
    }

    @Test
    void 关联读取绑定所有者且拒绝动态标识符注入() throws Exception {
        List<SqlDatasetDefinition> datasets =
                json.readValue(
                        Files.readString(Path.of("src/main/resources/export/datasets.json")),
                        new TypeReference<>() {});
        var link =
                datasets.stream()
                        .filter(d -> d.key().equals("device_expense"))
                        .findFirst()
                        .orElseThrow();
        String sql =
                ExportDatasetSql.select(
                        Map.of("definition", link, "ownerId", 7L, "cursor", "1", "limit", 500));
        assertEquals(3, sql.split("#\\{ownerId}", -1).length - 1);
        assertFalse(sql.contains("= 7"));
        assertTrue(sql.contains("d.is_deleted = 0"));
        assertTrue(sql.contains("d.id > #{cursor}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SqlDatasetDefinition.identifier("user; DROP TABLE user"));
    }
}
