package top.aiolife.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只连接测试自行创建的 PostgreSQL；不读取任何业务库连接。 */
class PostgresqlMigrationIT {
    @TempDir Path temporary;

    @Test void 初始化重复迁移增量事务回滚和结构预检() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-fixture")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            Path root = TestProject.copy(temporary);
            var configuration = Flyway.configure().dataSource(url, "postgres", "isolated-fixture")
                    .locations("filesystem:" + root.resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            var flyway = configuration.load();
            assertEquals(13, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url,"postgres","isolated-fixture")) {
                var snapshot = SchemaSnapshot.read(connection);
                assertEquals(68, snapshot.sections().get("tables").size());
                assertTrue(snapshot.sections().get("columns").stream().allMatch(c -> c.get(9)!=null && !c.get(9).isBlank()));
                assertEquals("2", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path IN ('/query/workbench','/query/models')").getFirst().getFirst());
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT mobile_is_enabled FROM sys_menu WHERE path='/query/models'").getFirst().getFirst());
                Path contract = temporary.resolve("contract.json");
                SchemaContract.write(contract,root,"20261112.13",connection);
                SchemaContract.read(contract,root).check(connection);
                SchemaContract.write(Path.of(System.getProperty("schema.reportDirectory", "target/schema"))
                        .resolve("schema-contract.json"), MigrationProject.root(), "20261112.13", connection);
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO sys_menu(id,name,path) VALUES(9001,'探针A','/CollationProbe')");
                    statement.execute("INSERT INTO sys_menu(id,name,path) VALUES(9002,'探针B','/collationprobe')");
                    statement.execute("INSERT INTO system_config(id,config_key,config_value) VALUES(9003,'CollationProbe','upper')");
                    statement.execute("INSERT INTO system_config(id,config_key,config_value) VALUES(9004,'collationprobe','lower')");
                }
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path='/CollationProbe'").getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path='/collationprobe'").getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path LIKE '/collation%'").getFirst().getFirst());
                assertEquals("upper", SchemaSnapshot.rows(connection, "SELECT config_value FROM system_config WHERE config_key='CollationProbe'").getFirst().getFirst());
                assertEquals("lower", SchemaSnapshot.rows(connection, "SELECT config_value FROM system_config WHERE config_key='collationprobe'").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO goal(id,user_id,title) VALUES(101,1,'fixture')");
                }
                Files.writeString(root.resolve("sql/migrations/V20261113_01__fixture.sql"),
                        "ALTER TABLE goal ADD COLUMN test_value integer DEFAULT 7; COMMENT ON COLUMN goal.test_value IS '测试字段';");
                assertEquals(1, configuration.load().migrate().migrationsExecuted);
                assertEquals("7", SchemaSnapshot.rows(connection,"SELECT test_value FROM goal WHERE id=101").getFirst().getFirst());
                Files.writeString(root.resolve("sql/migrations/V20261113_02__failure.sql"),
                        "CREATE TABLE rolled_back(id bigint); SELECT 1/0;");
                assertThrows(Exception.class, () -> configuration.load().migrate());
                assertNull(SchemaSnapshot.rows(connection,"SELECT to_regclass('rolled_back')").getFirst().getFirst());
                assertEquals("fixture",SchemaSnapshot.rows(connection,"SELECT title FROM goal WHERE id=101").getFirst().getFirst());
                assertThrows(Exception.class, () -> SchemaContract.read(contract,root));
            }
        }
    }

    @Test void 未知非空结构必须拒绝且不能自动登记() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD","isolated-fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2))) {
            container.start();
            String url="jdbc:postgresql://"+container.getHost()+":"+container.getMappedPort(5432)+"/postgres";
            try(var connection=DriverManager.getConnection(url,"postgres","isolated-fixture")) {
                connection.createStatement().execute("CREATE TABLE unknown(id bigint)");
            }
            var flyway=Flyway.configure().dataSource(url,"postgres","isolated-fixture")
                    .locations("filesystem:"+MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).callbacks(new MigrationGuard()).load();
            assertThrows(Exception.class,flyway::migrate);
            assertThrows(Exception.class,flyway::baseline);
        }
    }
}
