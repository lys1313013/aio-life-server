package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证衣柜季节由逗号文本升级为 jsonb 数组后内容不变且写入受取值约束。 */
class WardrobeSeasonJsonbMigrationIT {
    @Test void 季节增量转换旧CSV并拒绝非法取值() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-wardrobe-season")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-wardrobe-season")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.target("20261117.06").load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-wardrobe-season")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO wardrobe_item(id,name,user_id,season) VALUES"
                            + "(9007199254740993,'单季',7,'春'),"
                            + "(9007199254740994,'两季',7,'春,夏'),"
                            + "(9007199254740995,'带空格',7,' 秋 , 冬 '),"
                            + "(9007199254740996,'空串',7,''),"
                            + "(9007199254740997,'空值',7,NULL)");
                }
                assertEquals(1, config.target("20261117.07").load().migrate().migrationsExecuted);
                assertEquals(0, config.load().migrate().migrationsExecuted);
                assertEquals(List.of("jsonb"), SchemaSnapshot.rows(connection,
                        "SELECT data_type FROM information_schema.columns"
                                + " WHERE table_name='wardrobe_item' AND column_name='season'").getFirst());
                assertEquals(Arrays.asList("[\"春\"]", "[\"春\", \"夏\"]", "[\"秋\", \"冬\"]", null, null),
                        SchemaSnapshot.rows(connection, "SELECT season::text FROM wardrobe_item"
                                        + " WHERE id BETWEEN 9007199254740993 AND 9007199254740997 ORDER BY id")
                                .stream().map(row -> row.getFirst()).toList());
                try (var statement = connection.createStatement()) {
                    assertThrows(Exception.class, () -> statement.execute(
                            "UPDATE wardrobe_item SET season='[\"春\",\"未知\"]' WHERE id=9007199254740993"));
                    assertThrows(Exception.class, () -> statement.execute(
                            "UPDATE wardrobe_item SET season='[]' WHERE id=9007199254740993"));
                    assertThrows(Exception.class, () -> statement.execute(
                            "UPDATE wardrobe_item SET season='春,夏' WHERE id=9007199254740993"));
                    statement.execute("UPDATE wardrobe_item SET season='[\"夏\",\"冬\"]' WHERE id=9007199254740993");
                }
                assertEquals("[\"夏\", \"冬\"]", SchemaSnapshot.rows(connection,
                        "SELECT season::text FROM wardrobe_item WHERE id=9007199254740993").getFirst().getFirst());
            }
        }
    }
}
