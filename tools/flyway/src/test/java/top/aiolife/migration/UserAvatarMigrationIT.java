package top.aiolife.migration;

import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离 PostgreSQL 验证头像回填、异常回滚、旧关联解除与重跑。 */
class UserAvatarMigrationIT {
    @Test void 头像保留异常拒绝和删除字段可重跑() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-avatar")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-avatar")) {
                String migration = Files.readString(MigrationProject.root()
                        .resolve("sql/migrations/V20261115_01__user_avatar_use_file_binding.sql"));
                try (var statement = connection.createStatement()) {
                    statement.execute(Files.readString(MigrationProject.root()
                            .resolve("sql/migrations/V20261110_01__postgresql_schema.sql")));
                    statement.execute("INSERT INTO sys_user(id,username,nickname,avatar_file_id) VALUES(1,'one','甲','file-one'),(2,'two','乙','file-other')");
                    statement.execute("INSERT INTO file(id,file_name,biz_type,create_user) VALUES('file-one','one.png','avatar',1)");
                    statement.execute("INSERT INTO file(id,file_name,biz_type,create_user) VALUES('file-other','other.png','avatar',2)");
                    // 跨用户引用必须停止迁移，不能把他人文件绑定给当前用户。
                    statement.execute("INSERT INTO file(id,file_name,biz_type,create_user) VALUES('foreign','foreign.png','avatar',99)");
                    statement.execute("UPDATE sys_user SET avatar_file_id='foreign' WHERE id=2");
                }
                connection.setAutoCommit(false);
                assertThrows(Exception.class, () -> connection.createStatement().execute(migration));
                connection.rollback();
                connection.setAutoCommit(true);
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT count(*) FROM information_schema.columns WHERE column_name='avatar_file_id'").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE sys_user SET avatar_file_id=NULL WHERE id=2");
                    // 迁移前遗留的头像关联必须解除，不迁移到其他用户。
                    statement.execute("INSERT INTO file(id,file_name,biz_type,biz_id,create_user) VALUES('stale','stale.png','avatar',99,1)");
                }
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute(migration); }
                connection.commit();
                connection.setAutoCommit(true);
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM information_schema.columns WHERE column_name='avatar_file_id'").getFirst().getFirst());
                assertEquals("1", SchemaSnapshot.rows(connection, "SELECT biz_id FROM file WHERE id='file-one'").getFirst().getFirst());
                assertEquals("file-one", SchemaSnapshot.rows(connection, "SELECT id FROM file WHERE biz_type='avatar' AND biz_id=1").getFirst().getFirst());
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file WHERE biz_type='avatar' AND biz_id=2").getFirst().getFirst());
                assertEquals("2", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file WHERE id IN ('stale','file-other') AND biz_id IS NULL").getFirst().getFirst());
                try (var statement = connection.createStatement()) { statement.execute(migration); }
                assertThrows(Exception.class, () -> connection.createStatement().execute(
                        "INSERT INTO file(id,file_name,biz_type,biz_id,create_user) VALUES('duplicate','x.png','avatar',1,1)"));
            }
        }
    }
}
