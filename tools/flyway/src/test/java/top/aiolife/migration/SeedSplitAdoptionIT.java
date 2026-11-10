package top.aiolife.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 新旧初始化逐表等价；旧种子登记转换不得重放数据或留下部分账本。 */
class SeedSplitAdoptionIT {
    @TempDir Path temporary;

    private Map<String, String> data(Connection connection) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (var row : SchemaSnapshot.rows(connection,
                "SELECT tablename FROM pg_tables WHERE schemaname=current_schema() AND tablename<>'flyway_schema_history' ORDER BY tablename")) {
            String table = row.getFirst();
            String hash = SchemaSnapshot.rows(connection,
                    "SELECT md5(string_agg(to_jsonb(t)::text, '' ORDER BY to_jsonb(t)::text)) FROM \"" + table + "\" t").getFirst().getFirst();
            result.put(table, hash);
        }
        return result;
    }

    @Test void 分类初始化等价且旧库登记保留修改删除和业务数据_拒绝异常并事务回滚() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/postgres";
            Path root = TestProject.copy(temporary.resolve("current"));
            Path legacy = temporary.resolve("legacy/sql/migrations");
            Files.createDirectories(legacy);
            Files.copy(root.resolve("sql/migrations/V20261110_01__postgresql_schema.sql"), legacy.resolve("V20261110_01__postgresql_schema.sql"));
            Files.writeString(legacy.resolve("V20261110_02__public_seed_data.sql"), SeedSplitAdoption.sources(root).originalSql());
            var old = Flyway.configure().dataSource(url, "postgres", "isolated-fixture").locations("filesystem:" + legacy)
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard()).load();
            assertEquals(2, old.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-fixture")) {
                Map<String, String> oldData = data(connection);
                try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE fresh"); }
                String freshUrl = url.substring(0, url.lastIndexOf('/') + 1) + "fresh";
                var fresh = Flyway.configure().dataSource(freshUrl, "postgres", "isolated-fixture")
                        .locations("filesystem:" + root.resolve("sql/migrations"))
                        .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard()).load();
                assertEquals(12, fresh.migrate().migrationsExecuted);
                try (var freshConnection = DriverManager.getConnection(freshUrl, "postgres", "isolated-fixture")) {
                    assertEquals(oldData, data(freshConnection), "72 张表的全量种子内容必须与拆分前完全一致");
                }
                Path contract = temporary.resolve("contract.json");
                SchemaContract.write(contract, root, "20261110.02.011", connection);
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO goal(id,user_id,title) VALUES(101,1,'business-fixture')");
                    statement.execute("UPDATE sys_menu SET sort_order=12345 WHERE id=1001");
                    statement.execute("DELETE FROM sys_dict_data WHERE dict_value='ICBC'");
                }
                var before = data(connection);
                assertTrue(SeedSplitAdoption.run(connection, root, contract, null, false).contains("preflight passed"));
                assertEquals(2, SchemaSnapshot.rows(connection, "SELECT version FROM flyway_schema_history").size());
                assertEquals(before, data(connection));
                Path menus = root.resolve("sql/migrations/V20261110_02_006__menus.sql");
                String sql = Files.readString(menus);
                Files.writeString(menus, sql + "\nSELECT 1;\n");
                assertThrows(Exception.class, () -> SeedSplitAdoption.run(connection, root, contract, null, false));
                Files.writeString(menus, sql);
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE flyway_schema_history SET checksum=0 WHERE version='20261110.02'");
                }
                assertThrows(Exception.class, () -> SeedSplitAdoption.run(connection, root, contract, temporary.resolve("bad.json"), true));
                assertFalse(Files.exists(temporary.resolve("bad.json")));
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE flyway_schema_history SET checksum=" + SeedSplitAdoption.sources(root).originalChecksum() + " WHERE version='20261110.02'");
                    statement.execute("""
                            CREATE FUNCTION reject_split() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN IF NEW.version='20261110.02.002' THEN RAISE EXCEPTION 'fixture failure'; END IF; RETURN NEW; END $$;
                            CREATE TRIGGER reject_split BEFORE INSERT ON flyway_schema_history FOR EACH ROW EXECUTE FUNCTION reject_split()
                            """);
                }
                assertThrows(Exception.class, () -> SeedSplitAdoption.run(connection, root, contract, temporary.resolve("failed.json"), true));
                assertEquals(2, SchemaSnapshot.rows(connection, "SELECT version FROM flyway_schema_history").size());
                assertEquals(before, data(connection));
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP TRIGGER reject_split ON flyway_schema_history; DROP FUNCTION reject_split()");
                }
                Path backup = temporary.resolve("history-backup.json");
                assertTrue(SeedSplitAdoption.run(connection, root, contract, backup, true).contains("Registered 11"));
                assertTrue(Files.readString(backup).contains("V20261110_02__public_seed_data.sql"));
                assertEquals(before, data(connection));
                var current = Flyway.configure().dataSource(url, "postgres", "isolated-fixture")
                        .locations("filesystem:" + root.resolve("sql/migrations"))
                        .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard()).load();
                current.validate();
                assertEquals(0, current.migrate().migrationsExecuted);
                assertTrue(SeedSplitAdoption.run(connection, root, contract, backup, true).contains("already registered"));
                assertEquals(before, data(connection));
            }
        }
    }
}
