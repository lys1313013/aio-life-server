package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证影视和阅读的开始/完成列改名并改为日期后保留年月日、丢弃时分秒。 */
class MovieReadDateMigrationIT {
    @Test void 观影阅读日期改date并截断时分秒() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-library-date")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-library-date")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.target("20261117.07").load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-library-date");
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO movie(id,title,type,user_id,start_time,finish_time) VALUES"
                        + "(9007199254740993,'旧影片',1,7,'2019-02-03 04:05:00','2026-09-20 23:59:59'),"
                        + "(9007199254740994,'未开始影片',1,7,NULL,NULL)");
                statement.execute("INSERT INTO read_record(id,title,type,user_id,start_time,finish_time) VALUES"
                        + "(9007199254740995,'在读的书',1,7,'2026-01-01 10:00:00',NULL)");
            }

            assertEquals(1, config.target("20261117.08").load().migrate().migrationsExecuted);
            assertEquals(0, config.target("20261117.08").load().migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-library-date")) {
                assertEquals(java.util.List.of("date", "date", "date", "date"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name IN ('movie','read_record')"
                                + " AND column_name IN ('start_date','finish_date') ORDER BY table_name, column_name")
                        .stream().map(row -> row.getFirst()).toList());
                assertEquals(0, SchemaSnapshot.rows(connection,
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_name IN ('movie','read_record')"
                                + " AND column_name IN ('start_time','finish_time')").size());

                var movie = SchemaSnapshot.rows(connection,
                        "SELECT start_date::text, finish_date::text FROM movie WHERE id=9007199254740993").getFirst();
                assertEquals("2019-02-03", movie.get(0));
                assertEquals("2026-09-20", movie.get(1));
                var empty = SchemaSnapshot.rows(connection,
                        "SELECT start_date, finish_date FROM movie WHERE id=9007199254740994").getFirst();
                assertNull(empty.get(0));
                assertNull(empty.get(1));
                var read = SchemaSnapshot.rows(connection,
                        "SELECT start_date::text, finish_date FROM read_record WHERE id=9007199254740995").getFirst();
                assertEquals("2026-01-01", read.get(0));
                assertNull(read.get(1));

                String index = String.valueOf(SchemaSnapshot.rows(connection,
                        "SELECT indexdef FROM pg_indexes WHERE indexname='movie_idx_user_id'").getFirst().getFirst());
                assertTrue(index.contains("(user_id)"), index);
            }
        }
    }
}
