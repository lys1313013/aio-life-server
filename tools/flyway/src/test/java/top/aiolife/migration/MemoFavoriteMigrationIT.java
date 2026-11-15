package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证已有笔记升级后正文、归属和时间不变。 */
class MemoFavoriteMigrationIT {
    @Test void 收藏增量保留已有笔记并约束标记且不重复执行() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-memo")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-memo")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertEquals(18, config.target("20261115.01").load().migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-memo")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO memo(id,user_id,title,content,hidden_content,update_time) VALUES(9007199254740993,7,'模拟旧笔记','完整正文',1,'2026-11-01 12:00:00')");
                }
                assertEquals(1, config.target("20261115.02").load().migrate().migrationsExecuted);
                assertEquals(0, config.load().migrate().migrationsExecuted);
                assertEquals(java.util.List.of("7", "模拟旧笔记", "完整正文", "1", "0", "2026-11-01 12:00:00"),
                        SchemaSnapshot.rows(connection, "SELECT user_id,title,content,hidden_content,favorite,update_time FROM memo WHERE id=9007199254740993").getFirst());
                try (var statement = connection.createStatement()) {
                    assertThrows(Exception.class, () -> statement.execute("UPDATE memo SET favorite=2"));
                    assertThrows(Exception.class, () -> statement.execute("UPDATE memo SET favorite=NULL"));
                    statement.execute("UPDATE memo SET favorite=1");
                }
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT favorite FROM memo").getFirst().getFirst());
            }
        }
    }
}
