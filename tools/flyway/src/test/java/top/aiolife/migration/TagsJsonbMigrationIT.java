package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证目标与荣誉标签升级后标签内容不变且写入受 JSON 校验。 */
class TagsJsonbMigrationIT {
    @Test void 标签增量保留已有标签并拒绝非法JSON() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-tags")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-tags")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.target("20261116.05").load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-tags")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO goal(id,user_id,tags) VALUES"
                            + "(9007199254740993,7,'[\"阅读\",\"跑步\"]'),"
                            + "(9007199254740994,7,'工作,学习'),"
                            + "(9007199254740995,7,''),"
                            + "(9007199254740996,7,NULL)");
                    statement.execute("INSERT INTO honor_record(id,user_id,title,honor_date,tags) VALUES"
                            + "(9007199254740993,7,'模拟旧荣誉','2026-01-01','[\"校级\"]'),"
                            + "(9007199254740994,7,'模拟旧荣誉','2026-01-01','单标签'),"
                            + "(9007199254740995,7,'模拟旧荣誉','2026-01-01','')");
                }
                assertEquals(1, config.target("20261116.07").load().migrate().migrationsExecuted);
                assertEquals(0, config.load().migrate().migrationsExecuted);
                assertEquals(java.util.List.of("jsonb"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns WHERE table_name='goal' AND column_name='tags'").getFirst());
                assertEquals(java.util.List.of("jsonb"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns WHERE table_name='honor_record' AND column_name='tags'").getFirst());
                assertEquals(java.util.Arrays.asList("[\"阅读\", \"跑步\"]", "[\"工作,学习\"]", null, null), SchemaSnapshot.rows(connection,
                        "SELECT tags::text FROM goal WHERE id BETWEEN 9007199254740993 AND 9007199254740996 ORDER BY id").stream()
                        .map(row -> row.getFirst()).toList());
                assertEquals(java.util.Arrays.asList("[\"校级\"]", "[\"单标签\"]", null), SchemaSnapshot.rows(connection,
                        "SELECT tags::text FROM honor_record WHERE id BETWEEN 9007199254740993 AND 9007199254740995 ORDER BY id").stream()
                        .map(row -> row.getFirst()).toList());
                try (var statement = connection.createStatement()) {
                    assertThrows(Exception.class, () -> statement.execute("UPDATE goal SET tags='工作,学习' WHERE id=9007199254740993"));
                    assertThrows(Exception.class, () -> statement.execute("UPDATE goal SET tags='' WHERE id=9007199254740993"));
                    statement.execute("UPDATE goal SET tags='[\"阅读\"]' WHERE id=9007199254740993");
                }
                assertEquals("[\"阅读\"]", SchemaSnapshot.rows(connection,
                        "SELECT tags::text FROM goal WHERE id=9007199254740993").getFirst().getFirst());
            }
        }
    }
}
