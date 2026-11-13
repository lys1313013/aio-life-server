package top.aiolife.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
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
            assertEquals(17, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url,"postgres","isolated-fixture")) {
                var snapshot = SchemaSnapshot.read(connection);
                assertEquals(68, snapshot.sections().get("tables").size());
                assertTrue(snapshot.sections().get("columns").stream().allMatch(c -> c.get(9)!=null && !c.get(9).isBlank()));
                assertEquals("2", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path IN ('/query/workbench','/query/models')").getFirst().getFirst());
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT visible_clients & 2 FROM sys_menu WHERE path='/query/models'").getFirst().getFirst());
                Path contract = temporary.resolve("contract.json");
                SchemaContract.write(contract,root,"20261113.01",connection);
                SchemaContract.read(contract,root).check(connection);
                SchemaContract.write(Path.of(System.getProperty("schema.reportDirectory", "target/schema"))
                        .resolve("schema-contract.json"), MigrationProject.root(), "20261113.01", connection);
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO sys_menu(id,name,path) VALUES(9001,'探针A','/CollationProbe')");
                    statement.execute("INSERT INTO sys_menu(id,name,path) VALUES(9002,'探针B','/collationprobe')");
                    statement.execute("INSERT INTO sys_config(id,config_key,config_value) VALUES(9003,'CollationProbe','upper')");
                    statement.execute("INSERT INTO sys_config(id,config_key,config_value) VALUES(9004,'collationprobe','lower')");
                }
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path='/CollationProbe'").getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path='/collationprobe'").getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path LIKE '/collation%'").getFirst().getFirst());
                assertEquals("upper", SchemaSnapshot.rows(connection, "SELECT config_value FROM sys_config WHERE config_key='CollationProbe'").getFirst().getFirst());
                assertEquals("lower", SchemaSnapshot.rows(connection, "SELECT config_value FROM sys_config WHERE config_key='collationprobe'").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO goal(id,user_id,title) VALUES(101,1,'fixture')");
                }
                Files.writeString(root.resolve("sql/migrations/V20261114_01__fixture.sql"),
                        "ALTER TABLE goal ADD COLUMN test_value integer DEFAULT 7; COMMENT ON COLUMN goal.test_value IS '测试字段';");
                assertEquals(1, configuration.load().migrate().migrationsExecuted);
                assertEquals("7", SchemaSnapshot.rows(connection,"SELECT test_value FROM goal WHERE id=101").getFirst().getFirst());
                Files.writeString(root.resolve("sql/migrations/V20261114_02__failure.sql"),
                        "CREATE TABLE rolled_back(id bigint); SELECT 1/0;");
                assertThrows(Exception.class, () -> configuration.load().migrate());
                assertNull(SchemaSnapshot.rows(connection,"SELECT to_regclass('rolled_back')").getFirst().getFirst());
                assertEquals("fixture",SchemaSnapshot.rows(connection,"SELECT title FROM goal WHERE id=101").getFirst().getFirst());
                assertThrows(Exception.class, () -> SchemaContract.read(contract,root));
            }
        }
    }

    @Test void 删除分类时间类型保留公共覆盖私有层级和时迹() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-fixture")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var configuration = Flyway.configure().dataSource(url, "postgres", "isolated-fixture")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertEquals(13, configuration.target("20261112.13").load().migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-fixture")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO time_tracker_category
                              (id,user_id,name,parent_id,template_id,color,icon,description,is_track_time,is_enabled,sort_order,time_type)
                            VALUES
                              (71001,0,'模拟公共分类',0,NULL,'#1890ff','lucide:book','公共说明',1,1,10,1),
                              (71002,42,'模拟覆盖',NULL,71001,'#52c41a','lucide:check','覆盖说明',0,0,20,2),
                              (71003,42,'模拟私有子分类',71001,NULL,'#faad14','lucide:pen','子分类说明',1,1,30,3)
                            """);
                    statement.execute("""
                            INSERT INTO time_record
                              (id,user_id,category_id,date,start_minute,end_minute,duration_minutes,relate_id,relate_type)
                            VALUES (71004,42,71003,DATE '2026-11-12',60,89,30,91001,1)
                            """);
                }
                String categoriesSql = "SELECT to_jsonb(c) - 'time_type' FROM time_tracker_category c ORDER BY id";
                var categories = SchemaSnapshot.rows(connection, categoriesSql);
                var records = SchemaSnapshot.rows(connection, "SELECT * FROM time_record ORDER BY id");
                var types = SchemaSnapshot.rows(connection, "SELECT id,time_type FROM time_tracker_category ORDER BY id");
                String migration = Files.readString(MigrationProject.root()
                        .resolve("sql/migrations/V20261112_14__drop_time_category_time_type.sql"));
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute(migration);
                    assertThrows(SQLException.class, () -> statement.execute("SELECT 1/0"));
                } finally {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                assertEquals(types, SchemaSnapshot.rows(connection, "SELECT id,time_type FROM time_tracker_category ORDER BY id"));
                assertEquals(1, configuration.target("20261112.14").load().migrate().migrationsExecuted);
                assertEquals("0", SchemaSnapshot.rows(connection, """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema=current_schema() AND table_name='time_tracker_category' AND column_name='time_type'
                        """).getFirst().getFirst());
                assertEquals(categories, SchemaSnapshot.rows(connection, categoriesSql));
                assertEquals(records, SchemaSnapshot.rows(connection, "SELECT * FROM time_record ORDER BY id"));
                assertEquals(0, configuration.load().migrate().migrationsExecuted);
                try (var statement = connection.createStatement()) {
                    statement.execute(migration);
                }
                assertEquals(categories, SchemaSnapshot.rows(connection, categoriesSql));
            }
        }
    }

    @Test void 分类菜单迁移保留锁隐藏快捷导航与菜单标识() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-fixture")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var configuration = Flyway.configure().dataSource(url, "postgres", "isolated-fixture")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertEquals(14, configuration.target("20261112.14").load().migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-fixture")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO user_secondary_lock_menu(id,user_id,menu_id) VALUES(71001,42,1303)");
                    statement.execute("INSERT INTO user_menu_hidden(id,user_id,menu_id) VALUES(71002,42,1508)");
                    statement.execute("INSERT INTO user_quick_nav(id,user_id,menu_id,sort_order) VALUES(71003,42,1304,20)");
                }
                var paths = SchemaSnapshot.rows(connection, "SELECT id,path,web_is_enabled,mobile_is_enabled FROM sys_menu WHERE id IN (1303,1304,1508) ORDER BY id");
                var locks = SchemaSnapshot.rows(connection, "SELECT * FROM user_secondary_lock_menu ORDER BY id");
                var hidden = SchemaSnapshot.rows(connection, "SELECT * FROM user_menu_hidden ORDER BY id");
                var shortcuts = SchemaSnapshot.rows(connection, "SELECT * FROM user_quick_nav ORDER BY id");
                assertEquals(1, configuration.target("20261112.15").load().migrate().migrationsExecuted);
                assertEquals(paths, SchemaSnapshot.rows(connection, "SELECT id,path,web_is_enabled,mobile_is_enabled FROM sys_menu WHERE id IN (1303,1304,1508) ORDER BY id"));
                assertEquals(locks, SchemaSnapshot.rows(connection, "SELECT * FROM user_secondary_lock_menu ORDER BY id"));
                assertEquals(hidden, SchemaSnapshot.rows(connection, "SELECT * FROM user_menu_hidden ORDER BY id"));
                assertEquals(shortcuts, SchemaSnapshot.rows(connection, "SELECT * FROM user_quick_nav ORDER BY id"));
                assertEquals("2", SchemaSnapshot.rows(connection, """
                        SELECT count(*) FROM sys_menu c JOIN sys_menu p ON c.parent_id=p.id
                        WHERE c.id IN (1303,1508) AND p.path='/profile/category-management'
                          AND c.roles IS NULL AND c.meta->>'title' IN ('通用分类','时迹分类')
                        """).getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, """
                        SELECT count(*) FROM sys_menu c JOIN sys_menu p ON c.parent_id=p.id
                        WHERE c.id=1304 AND p.path='/system' AND c.roles='admin' AND c.meta->>'title'='时迹分类'
                        """).getFirst().getFirst());
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE parent_id=1300 AND id IN (1303,1304)").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    statement.execute(Files.readString(MigrationProject.root()
                            .resolve("sql/migrations/V20261112_15__relocate_category_menus.sql")));
                }
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM sys_menu WHERE path='/profile/category-management'").getFirst().getFirst());
                assertEquals(0, configuration.load().migrate().migrationsExecuted);
            }
        }
    }

    @Test void 展示端增量合并旧状态保留锁并删除旧列且拒绝未知位() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_DB", "fixture").withEnv("POSTGRES_PASSWORD", "isolated-fixture")
                .withExposedPorts(5432).waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var configuration = Flyway.configure().dataSource(url, "postgres", "isolated-fixture")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertEquals(15, configuration.target("20261112.15").load().migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-fixture")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE sys_menu SET mobile_is_enabled=0 WHERE id=1304");
                    statement.execute("UPDATE sys_menu SET web_is_enabled=0,mobile_is_enabled=1 WHERE id=1900");
                    statement.execute("UPDATE sys_menu SET web_is_enabled=0,mobile_is_enabled=0 WHERE id=1303");
                    statement.execute("INSERT INTO user_secondary_lock_menu(id,user_id,menu_id) VALUES(71001,42,1301)");
                }
                var before = SchemaSnapshot.rows(connection, "SELECT id,path,roles FROM sys_menu ORDER BY id");
                var masks = SchemaSnapshot.rows(connection, "SELECT id,(web_is_enabled::INTEGER | (mobile_is_enabled::INTEGER << 1)) & CASE WHEN path='/time/time-tracker' THEN ~2 ELSE ~0 END FROM sys_menu ORDER BY id");
                var locks = SchemaSnapshot.rows(connection, "SELECT * FROM user_secondary_lock_menu ORDER BY id");
                assertEquals(1, configuration.target("20261112.16").load().migrate().migrationsExecuted);
                assertEquals(before, SchemaSnapshot.rows(connection, "SELECT id,path,roles FROM sys_menu ORDER BY id"));
                assertEquals(masks, SchemaSnapshot.rows(connection, "SELECT id,visible_clients FROM sys_menu ORDER BY id"));
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM information_schema.columns WHERE table_name='sys_menu' AND column_name IN ('web_is_enabled','mobile_is_enabled')").getFirst().getFirst());
                assertEquals(locks, SchemaSnapshot.rows(connection, "SELECT * FROM user_secondary_lock_menu ORDER BY id"));
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT visible_clients FROM sys_menu WHERE path='/time/time-tracker'").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    for (int mask : List.of(0, 1, 2, 3)) statement.execute("UPDATE sys_menu SET visible_clients=" + mask + " WHERE id=1301");
                    assertThrows(SQLException.class, () -> statement.execute("UPDATE sys_menu SET visible_clients=4 WHERE id=1301"));
                    assertThrows(SQLException.class, () -> statement.execute("UPDATE sys_menu SET visible_clients=-1 WHERE id=1301"));
                    assertThrows(SQLException.class, () -> statement.execute("UPDATE sys_menu SET visible_clients=NULL WHERE id=1301"));
                }
                assertEquals(0, configuration.load().migrate().migrationsExecuted);
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
