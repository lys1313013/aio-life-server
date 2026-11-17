package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证活动日期改日期列时拒绝丢失时分秒，截断后结构、数据与索引正确。 */
class EventDateMigrationIT {
    @Test void 活动日期改为date且带时分秒时拒绝执行() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-event-date")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-event-date")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.target("20261116.12").load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-event-date");
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO event_record(id,user_id,title,event_type,event_date,city,venue,ticket_price) VALUES"
                        + "(9007199254740993,7,'旧活动','concert','2026-09-15 19:30:00','city','venue',19.99),"
                        + "(9007199254740994,7,'整日活动','concert','2026-10-01 00:00:00','city','venue',20.00)");
                var blocked = assertThrows(Exception.class, () -> config.target("20261117.01").load().migrate());
                assertTrue(String.valueOf(blocked.getMessage()).contains("存在带时分秒的活动日期"), blocked.getMessage());
                assertEquals(java.util.List.of("timestamp without time zone"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns WHERE table_name='event_record' AND column_name='event_date'").getFirst());

                statement.execute("UPDATE event_record SET event_date=date_trunc('day',event_date) WHERE id=9007199254740993");
                assertEquals(1, config.target("20261117.01").load().migrate().migrationsExecuted);
                assertEquals(0, config.target("20261117.01").load().migrate().migrationsExecuted);

                assertEquals(java.util.List.of("date"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns WHERE table_name='event_record' AND column_name='event_date'").getFirst());
                assertEquals(java.util.Arrays.asList("2026-09-15", "2026-10-01"), SchemaSnapshot.rows(connection,
                                "SELECT event_date::text FROM event_record ORDER BY id").stream()
                        .map(row -> row.getFirst()).toList());
                String index = String.valueOf(SchemaSnapshot.rows(connection,
                        "SELECT indexdef FROM pg_indexes WHERE indexname='event_record_idx_user_deleted_date'").getFirst().getFirst());
                assertTrue(index.contains("event_date DESC"), index);
            }
        }
    }
}
