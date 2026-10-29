package top.aiolife.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 用户确认历史 SQL 已完成：原生 baseline 只登记边界，不代表结构差异已修复。 */
class AdoptionIT {
    @Test void 登记现有版本不重跑历史脚本且只执行后续增量(@TempDir Path temporary) throws Exception {
        Path root = TestProject.copy(temporary);
        String version = System.getProperty("adoption.version");
        // 固定历史接入区间；未来新增真实迁移不会被误判为应该跳过的旧脚本。
        try (var scripts = Files.list(root.resolve("sql/migrations"))) {
            for (Path script : scripts.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String name = script.getFileName().toString();
                var scriptVersion = MigrationVersion.fromVersion(name.substring(1, name.indexOf("__")));
                if (scriptVersion.compareTo(MigrationVersion.fromVersion(version)) > 0) Files.delete(script);
            }
        }
        for (String image : List.of("mysql:8.0.40", "mysql:8.4.2")) {
            try (var mysql = new IsolatedMysql(image, root)) {
                String db = mysql.database();
                // 故意不使用新库结构，任何历史脚本误执行都会失败或改变快照。
                mysql.sql(db, "CREATE TABLE adoption_probe (id BIGINT PRIMARY KEY COMMENT '主键', value VARCHAR(32) COMMENT '保留内容') ENGINE=InnoDB COMMENT='接入保留验证'; INSERT INTO adoption_probe VALUES(1,'keep existing data')");
                var beforeSchema = mysql.snapshot(db);
                var beforeData = mysql.data(db);
                Flyway.configure().dataSource(mysql.url(db), "root", mysql.password)
                        .locations("filesystem:" + root.resolve("sql/migrations"))
                        .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false)
                        .baselineVersion(version).baselineDescription("Existing SQL confirmed applied; no replay")
                        .load().baseline();
                assertEquals(beforeSchema, mysql.snapshot(db));
                assertEquals(beforeData, mysql.data(db));
                assertEquals(List.of(List.of(version, "BASELINE", "1")),
                        mysql.rows(db, "SELECT version,type,success FROM flyway_schema_history"));
                assertEquals(0, mysql.flyway(db).info().pending().length);
                assertEquals(0, mysql.flyway(db).migrate().migrationsExecuted);
                mysql.flyway(db).validate();
                assertEquals(beforeSchema, mysql.snapshot(db));
                assertEquals(beforeData, mysql.data(db));
                Path future = root.resolve("sql/migrations/V99999999_01__adoption_probe.sql");
                try {
                    Files.writeString(future, "ALTER TABLE adoption_probe ADD COLUMN note VARCHAR(32) NULL COMMENT '后续增量';");
                    assertEquals(1, mysql.flyway(db).migrate().migrationsExecuted);
                    assertEquals("keep existing data", mysql.scalar(db, "SELECT value FROM adoption_probe WHERE id=1"));
                    assertEquals("2", mysql.scalar(db, "SELECT COUNT(*) FROM flyway_schema_history"));
                    assertEquals(0, mysql.flyway(db).migrate().migrationsExecuted);
                } finally { Files.deleteIfExists(future); }
            }
        }
    }
}
