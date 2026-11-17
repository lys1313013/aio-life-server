package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.junit.jupiter.api.Assertions.*;

/** API Key 改为 SHA-256 摘要：删除明文列并清空旧 Key，其他表数据保留。 */
class ApiKeyHashMigrationIT {
    @Test
    void 删除明文列并清空旧Key且重复迁移不重跑() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-api-key-hash")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-api-key-hash")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            config.target("20261117.04").load().migrate();
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-api-key-hash");
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO api_key(id,user_id,api_key,remark)"
                        + " VALUES(9007199254740993,42,'ak-old-plain-key','旧密钥')");
                statement.execute("INSERT INTO api_key_log(id,api_key_id,request_path,request_method)"
                        + " VALUES(9007199254740994,9007199254740993,'/expense','GET')");
                assertEquals(1, config.target("20261117.05").load().migrate().migrationsExecuted);
                assertEquals(0, config.target("20261117.05").load().migrate().migrationsExecuted);
                config.load().validate();
                assertEquals("0", SchemaSnapshot.rows(connection,
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()"
                                + " AND table_name='api_key' AND column_name='api_key'")
                        .getFirst().getFirst());
                assertEquals(List.of(List.of("api_key_hash"), List.of("key_prefix")), SchemaSnapshot.rows(connection,
                        "SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema()"
                                + " AND table_name='api_key' AND column_name IN ('api_key_hash','key_prefix')"
                                + " ORDER BY column_name"));
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM api_key")
                        .getFirst().getFirst());
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM api_key_log")
                        .getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection,
                        "SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema()"
                                + " AND tablename='api_key' AND indexname='uk_api_key_hash'")
                        .getFirst().getFirst());
                var comments = SchemaSnapshot.rows(connection,
                        "SELECT col_description('api_key'::regclass,attnum) FROM pg_attribute"
                                + " WHERE attrelid='api_key'::regclass AND attname IN ('api_key_hash','key_prefix')");
                assertEquals(2, comments.size());
                assertTrue(comments.stream().allMatch(row -> row.getFirst().contains("SHA-256")
                        || row.getFirst().contains("前12位")));
            }
        }
    }
}
