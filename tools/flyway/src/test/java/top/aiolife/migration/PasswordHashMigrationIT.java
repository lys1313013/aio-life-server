package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.junit.jupiter.api.Assertions.*;

/** bcrypt 结构升级只移除独立盐列，不转换或清空任何现存密码。 */
class PasswordHashMigrationIT {
    @Test
    void 删除独立盐列并保留用户及其他字段且重复迁移不重跑() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-password-hash")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-password-hash")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            config.target("20261117.02").load().migrate();
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-password-hash");
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO sys_user(id,username,nickname,password,password_salt,secondary_password,secondary_password_salt)"
                        + " VALUES(9007199254740993,'bcrypt-fixture','测试用户','untouched-password','salt','untouched-secondary','secondary-salt')");
                assertEquals(1, config.target("20261117.03").load().migrate().migrationsExecuted);
                assertEquals(0, config.target("20261117.03").load().migrate().migrationsExecuted);
                config.load().validate();
                assertEquals("0", SchemaSnapshot.rows(connection,
                        "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()"
                                + " AND table_name='sys_user' AND column_name IN ('password_salt','secondary_password_salt')")
                        .getFirst().getFirst());
                assertEquals(List.of("9007199254740993", "bcrypt-fixture", "测试用户", "untouched-password", "untouched-secondary"),
                        SchemaSnapshot.rows(connection,
                                "SELECT id::text,username,nickname,password,secondary_password FROM sys_user WHERE id=9007199254740993")
                                .getFirst());
                var comments = SchemaSnapshot.rows(connection,
                        "SELECT col_description('sys_user'::regclass,attnum) FROM pg_attribute"
                                + " WHERE attrelid='sys_user'::regclass AND attname IN ('password','secondary_password')");
                assertEquals(2, comments.size());
                assertTrue(comments.stream().allMatch(row -> row.getFirst().contains("bcrypt")));
            }
        }
    }
}
