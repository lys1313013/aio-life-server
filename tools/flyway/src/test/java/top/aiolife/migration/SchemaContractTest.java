package top.aiolife.migration;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SchemaContractTest {
    private SchemaSnapshot fixture() {
        Map<String, List<List<String>>> sections = new HashMap<>();
        sections.put("tables", List.of(List.of("t", "InnoDB", "utf8mb4_unicode_ci", "中文表")));
        sections.put("columns", List.of(Arrays.asList("t", "c", "bigint", "YES", null, "", "", null, null, "中文字段")));
        sections.put("indexes", List.of(Arrays.asList("t", "i", "1", "1", "c", null, "BTREE", "A", "YES", null)));
        sections.put("checks", List.of(List.of("t", "positive", "(`c` > 0)", "YES")));
        sections.put("foreign_keys", List.of(List.of("t", "fk", "c", "1", "parent", "id", "RESTRICT", "RESTRICT")));
        return new SchemaSnapshot(sections);
    }
    @ParameterizedTest @ValueSource(strings = {"tables", "columns", "indexes", "checks", "foreign_keys"})
    void 任何结构分区差异均被报告(String section) {
        var expected = fixture(); var changed = new HashMap<>(expected.sections()); changed.put(section, List.of());
        assertFalse(expected.differences(new SchemaSnapshot(changed)).isEmpty());
    }
    @Test void 拒绝旧格式和过期源码摘要() throws Exception {
        Path file = Files.createTempFile("aio-contract-", ".json");
        try {
            var sources = MigrationProject.sources(MigrationProject.root());
            SchemaContract.JSON.writeValue(file.toFile(), new SchemaContract(3, "1", "8.0.40", sources, fixture(), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> SchemaContract.read(file, MigrationProject.root()));
            sources.put("sql/migrations/fake.sql", "changed");
            SchemaContract.JSON.writeValue(file.toFile(), new SchemaContract(SchemaContract.FORMAT, "1", "8.0.40", sources, fixture(), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> SchemaContract.read(file, MigrationProject.root()));
        } finally { Files.deleteIfExists(file); }
    }
    @ParameterizedTest @ValueSource(strings = {"pomComment", "pomWhitespace", "javaComment", "testDependency"})
    void 工具文本和测试依赖变更不使契约失效(String change, @org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
        Path root = TestProject.copy(temporary);
        Path contract = root.resolve("contract.json");
        var evidence = MigrationProject.toolSources(root);
        SchemaContract.JSON.writeValue(contract.toFile(), new SchemaContract(SchemaContract.FORMAT, "20261028.04", "8.0.40", MigrationProject.sources(root), fixture(), evidence));
        Path file = root.resolve(change.equals("javaComment") ? "tools/flyway/src/main/java/top/aiolife/migration/MigrationGuard.java" : "tools/flyway/pom.xml");
        String source = Files.readString(file);
        String edited = switch (change) {
            case "pomComment" -> source.replace("<!-- 独立工具，不进入应用依赖或应用启动流程。 -->", "<!-- only a comment -->");
            case "pomWhitespace" -> source.replace("<encoding>UTF-8</encoding>", "<encoding>\n  UTF-8\n</encoding>");
            case "javaComment" -> source + "\n// 仅修改注释\n";
            case "testDependency" -> source.replace("<version>6.1.3</version>", "<version>6.1.4</version>");
            default -> throw new IllegalArgumentException(change);
        };
        assertNotEquals(source, edited); Files.writeString(file, edited);
        assertNotEquals(evidence, MigrationProject.toolSources(root));
        assertDoesNotThrow(() -> SchemaContract.read(contract, root));
    }
    @ParameterizedTest @ValueSource(strings = {"sqlEdit", "sqlAdd", "sqlRemove", "encoding", "flywayVersion", "adoptionVersion", "driverVersion"})
    void SQL及迁移配置变化必须重新生成契约(String change, @org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
        Path root = TestProject.copy(temporary);
        Path contract = root.resolve("contract.json");
        SchemaContract.JSON.writeValue(contract.toFile(), new SchemaContract(SchemaContract.FORMAT, "20261028.04", "8.0.40", MigrationProject.sources(root), fixture(), MigrationProject.toolSources(root)));
        Path sql = root.resolve("sql/migrations/V20261028_06__restore_scheduled_jobs_menu.sql");
        Path pom = root.resolve("tools/flyway/pom.xml");
        String source = Files.readString(pom);
        switch (change) {
            case "sqlEdit" -> Files.writeString(sql, Files.readString(sql) + "\nSELECT 1;\n");
            case "sqlAdd" -> Files.writeString(root.resolve("sql/migrations/V99999999_01__probe.sql"), "SELECT 1;");
            case "sqlRemove" -> Files.delete(sql);
            case "encoding" -> Files.writeString(pom, source.replace("<encoding>UTF-8</encoding>", "<encoding>UTF-16</encoding>"));
            case "flywayVersion" -> Files.writeString(pom, source.replace("<flyway.version>13.11.1</flyway.version>", "<flyway.version>13.11.2</flyway.version>"));
            case "adoptionVersion" -> Files.writeString(pom, source.replace("<adoption.version>20261028.06</adoption.version>", "<adoption.version>20261028.07</adoption.version>"));
            case "driverVersion" -> Files.writeString(pom, source.replace("<version>3.5.11</version>", "<version>3.5.12</version>"));
            default -> throw new IllegalArgumentException(change);
        }
        var error = assertThrows(IllegalArgumentException.class, () -> SchemaContract.read(contract, root));
        assertTrue(error.getMessage().contains("SQL or migration settings hashes differ"));
    }
}
