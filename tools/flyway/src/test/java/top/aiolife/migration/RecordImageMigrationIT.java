package top.aiolife.migration;

import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离 PostgreSQL 验证历史单图数据回填、异常回滚、共享图片与重跑。 */
class RecordImageMigrationIT {
    @Test void 原图片保留异常拒绝和删除字段可重跑() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-images")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-images")) {
                String migration = Files.readString(MigrationProject.root()
                        .resolve("sql/migrations/V20261113_01__record_images_use_file_binding.sql"));
                try (var statement = connection.createStatement()) {
                    statement.execute(Files.readString(MigrationProject.root()
                            .resolve("sql/migrations/V20261110_01__postgresql_schema.sql")));
                    for (String table : List.of("device", "movie", "read_record", "wardrobe_item")) {
                        statement.execute("ALTER TABLE " + table + " ADD COLUMN file_id varchar(32)");
                        String fields = table.equals("movie") || table.equals("read_record") ? "title,type" : "name";
                        String values = fields.equals("name") ? "'保留名称'" : "'保留名称',1";
                        statement.execute("INSERT INTO " + table + "(id,user_id,file_id," + fields
                                + ") VALUES(1,42,'" + table + "'," + values + "),(2,42,NULL," + values + ")");
                        String type = table.equals("read_record") ? "read" : table;
                        statement.execute("INSERT INTO file(id,file_name,biz_type,create_user) VALUES('"
                                + table + "','retained-object.png','" + type + "',42)");
                        // 与原 file_id 不一致的旧关联必须解除，但不得删除文件。
                        statement.execute("INSERT INTO file(id,file_name,biz_type,biz_id,create_user) VALUES('stale-"
                                + table + "','old-object.png','" + type + "',1,42)");
                    }
                    statement.execute("UPDATE device SET file_id='missing' WHERE id=2");
                }
                connection.setAutoCommit(false);
                assertThrows(Exception.class, () -> connection.createStatement().execute(migration));
                connection.rollback();
                connection.setAutoCommit(true);
                assertEquals("4", SchemaSnapshot.rows(connection, "SELECT count(*) FROM information_schema.columns WHERE column_name='file_id'").getFirst().getFirst());
                try (var statement = connection.createStatement()) {
                    // 同一张未绑定图片被两个历史设备引用，迁移后各自保留独立附件。
                    statement.execute("UPDATE device SET file_id='device' WHERE id=2");
                }
                var before = SchemaSnapshot.rows(connection, "SELECT to_jsonb(d)-'file_id' FROM device d ORDER BY id");
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) { statement.execute(migration); }
                connection.commit();
                connection.setAutoCommit(true);
                assertEquals("0", SchemaSnapshot.rows(connection, "SELECT count(*) FROM information_schema.columns WHERE column_name='file_id'").getFirst().getFirst());
                assertEquals(before, SchemaSnapshot.rows(connection, "SELECT to_jsonb(d) FROM device d ORDER BY id"));
                assertEquals("5", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file WHERE biz_id IS NOT NULL").getFirst().getFirst());
                assertEquals("4", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file WHERE id LIKE 'stale-%' AND biz_id IS NULL AND is_deleted=0").getFirst().getFirst());
                assertEquals("2", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file WHERE biz_type='device' AND biz_id IN(1,2) AND file_name='retained-object.png'").getFirst().getFirst());
                try (var statement = connection.createStatement()) { statement.execute(migration); }
                assertEquals("9", SchemaSnapshot.rows(connection, "SELECT count(*) FROM file").getFirst().getFirst());
                assertThrows(Exception.class, () -> connection.createStatement().execute(
                        "INSERT INTO file(id,file_name,biz_type,biz_id,create_user) VALUES('duplicate','x.png','device',1,42)"));
            }
        }
    }
}
