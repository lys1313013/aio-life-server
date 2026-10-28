package top.aiolife.migration;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaTest {
    @Test void versionBoundaries() {
        assertFalse(SchemaSnapshot.Version.parse("5.7.44-log").enforcesChecks());
        assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.Version.parse("8.0.15"));
        assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.Version.parse("5.7.43"));
        assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.Version.parse("5.6.51"));
        assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.Version.parse("9.0.0"));
        assertTrue(SchemaSnapshot.Version.parse("8.4.2").enforcesChecks());
        assertTrue(SchemaSnapshot.Version.parse("8.0.16").enforcesChecks());
        assertThrows(IllegalArgumentException.class, () -> SchemaSnapshot.Version.parse("10.6.0-MariaDB"));
    }
    @Test void expressionsPreserveStringsAndGrouping() {
        assertEquals(SchemaSnapshot.expression("((A + B))"), SchemaSnapshot.expression("a+b"));
        assertNotEquals(SchemaSnapshot.expression("(a+b)*c"), SchemaSnapshot.expression("a+b*c"));
        assertNotEquals(SchemaSnapshot.expression("x='A B'"), SchemaSnapshot.expression("x='ab'"));
    }
    @Test void metadataUsesCompatibleColumns() {
        assertFalse(SchemaSnapshot.queries(SchemaSnapshot.Version.parse("5.7.44")).get("indexes").contains("is_visible"));
        assertFalse(SchemaSnapshot.queries(SchemaSnapshot.Version.parse("5.7.44")).get("checks").contains("check_constraints"));
        assertTrue(SchemaSnapshot.queries(SchemaSnapshot.Version.parse("8.0.40")).get("indexes").contains("expression"));
    }
    @Test void normalizationRetainsMeaningfulAttributes() {
        var row = Arrays.asList("t", "c", "bigint(20)", "YES", null, "", "", null, null, "中文");
        var normalized = SchemaSnapshot.normalize("columns", row);
        assertEquals("bigint", normalized.get(2));
        assertEquals("中文", normalized.get(9));
        row.set(2, "bigint(20) unsigned zerofill");
        assertEquals(row.get(2), SchemaSnapshot.normalize("columns", row).get(2));
    }
    @Test void unsafeOverridesRejected() {
        var config = Flyway.configure().validateMigrationNaming(true);
        MigrationGuard.checkConfiguration(config);
        assertThrows(IllegalArgumentException.class, () -> MigrationGuard.checkConfiguration(config.baselineOnMigrate(true)));
        config.baselineOnMigrate(false).cleanDisabled(false);
        assertThrows(IllegalArgumentException.class, () -> MigrationGuard.checkConfiguration(config));
        config.cleanDisabled(true).skipExecutingMigrations(true);
        assertThrows(IllegalArgumentException.class, () -> MigrationGuard.checkConfiguration(config));
    }
    @Test void incompleteContractsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SchemaSnapshot(Map.of()).validate());
    }
    @Test void 强校验包含SQL和配置但工具源码仅用于追溯() throws Exception {
        var sources = MigrationProject.sources(MigrationProject.root());
        assertTrue(sources.containsKey("flyway.settings"));
        assertFalse(sources.containsKey("tools/flyway/pom.xml"));
        assertTrue(MigrationProject.toolSources(MigrationProject.root()).containsKey("tools/flyway/src/main/java/top/aiolife/migration/MigrationGuard.java"));
        assertTrue(sources.containsKey("sql/migrations/B20261028_00__baseline.sql"));
        assertFalse(sources.keySet().stream().anyMatch(key -> key.endsWith(".py")));
    }
}
