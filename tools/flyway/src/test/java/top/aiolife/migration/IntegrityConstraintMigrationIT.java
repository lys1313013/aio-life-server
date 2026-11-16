package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证约束增量的去重、必填回填和重复执行行为。 */
class IntegrityConstraintMigrationIT {
    @Test void 完整性增量清理重复并补齐约束且不重复执行() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-integrity")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-integrity")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.target("20261116.04").load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-integrity")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO notification_delivery(id,dedup_key,user_id,biz_type,channel,status,payload_ciphertext,create_time) VALUES
                              (901,'dup',1,'LEETCODE_REMINDER','FEISHU','PENDING','x','2026-11-01 10:00:00'),
                              (902,'dup',1,'LEETCODE_REMINDER','FEISHU','PENDING','x','2026-11-01 10:05:00')
                            """);
                    statement.execute("""
                            INSERT INTO device(id,name,user_id,source_platform,source_order_id,source_product_id,source_item_id,source_unit_index,create_time) VALUES
                              (911,'A',1,'jd','order-1','sku-1','line-1',1,'2026-11-01 10:00:00'),
                              (912,'B',1,'jd','order-1','sku-1','line-1',1,'2026-11-01 10:05:00')
                            """);
                    statement.execute("""
                            INSERT INTO wardrobe_item(id,name,user_id,source_platform,source_order_id,source_product_id,source_item_id,source_unit_index,create_time) VALUES
                              (913,'A',1,'jd','order-1','sku-1','line-1',1,'2026-11-01 10:00:00'),
                              (914,'B',1,'jd','order-1','sku-1','line-1',1,'2026-11-01 10:05:00')
                            """);
                    statement.execute("INSERT INTO user_quick_nav(id,user_id,menu_id) VALUES(921,1,10),(922,1,10)");
                    statement.execute("""
                            INSERT INTO user_secondary_lock_menu(id,user_id,menu_id,create_time) VALUES
                              (931,1,10,'2026-11-01 10:00:00'),
                              (932,1,10,'2026-11-02 10:00:00')
                            """);
                    statement.execute("INSERT INTO memo(id,user_id,hidden_content) VALUES(941,1,NULL)");
                    statement.execute("INSERT INTO thought(id,user_id,content,hidden_content) VALUES(942,1,'x',NULL)");
                    statement.execute("""
                            INSERT INTO movie(id,user_id,title,type,status,total_progress,current_progress) VALUES
                              (943,1,'m',1,NULL,NULL,NULL)
                            """);
                    statement.execute("""
                            INSERT INTO read_record(id,user_id,title,type,status,total_progress,current_progress) VALUES
                              (949,1,'r',1,NULL,NULL,NULL)
                            """);
                    statement.execute("""
                            INSERT INTO honor_record(id,user_id,title,honor_date,is_top,is_public,sort_order) VALUES
                              (944,1,'h','2026-01-01',NULL,NULL,NULL)
                            """);
                    statement.execute("""
                            INSERT INTO membership_record(id,user_id,name,expiry_date,auto_renew) VALUES
                              (945,1,'m','2026-12-31',NULL)
                            """);
                    statement.execute("INSERT INTO goal(id,user_id,status) VALUES(946,1,NULL)");
                    statement.execute("INSERT INTO honor_category(id,name,sort_order) VALUES(947,'c',NULL)");
                    statement.execute("INSERT INTO wardrobe_category(id,name,category_type,sort_order) VALUES(948,'w',NULL,NULL)");
                    statement.execute("INSERT INTO task(id,user_id,sort_order) VALUES(950,1,NULL)");
                    statement.execute("INSERT INTO sys_dict_data(dict_code,dict_id,dict_value,is_default) VALUES(951,1,'a','Y'),(952,1,'b','N')");
                    statement.execute("""
                            INSERT INTO user_dict_data(id,user_id,is_default,is_readonly) VALUES
                              (953,1,'Y','N'),
                              (954,1,'N','Y')
                            """);
                }
                assertEquals(1, config.target("20261116.05").load().migrate().migrationsExecuted);
                assertEquals(0, config.load().migrate().migrationsExecuted);
                assertEquals(java.util.List.of("901"),
                        SchemaSnapshot.rows(connection, "SELECT id FROM notification_delivery WHERE is_deleted=0").getFirst());
                assertEquals(java.util.List.of("jd", "order-1", "line-1", "1"),
                        SchemaSnapshot.rows(connection, "SELECT source_platform,source_order_id,source_item_id,source_unit_index FROM device WHERE id=911").getFirst());
                assertEquals(java.util.Arrays.asList(null, null, null, null),
                        SchemaSnapshot.rows(connection, "SELECT source_platform,source_order_id,source_item_id,source_unit_index FROM device WHERE id=912").getFirst());
                assertEquals(java.util.Arrays.asList(null, null, null, null),
                        SchemaSnapshot.rows(connection, "SELECT source_platform,source_order_id,source_item_id,source_unit_index FROM wardrobe_item WHERE id=914").getFirst());
                assertEquals(java.util.List.of("1"),
                        SchemaSnapshot.rows(connection, "SELECT count(*) FROM user_quick_nav WHERE user_id=1 AND menu_id=10").getFirst());
                assertEquals(java.util.List.of("931"),
                        SchemaSnapshot.rows(connection, "SELECT id FROM user_secondary_lock_menu WHERE is_deleted=0").getFirst());
                assertEquals(java.util.List.of("0"), SchemaSnapshot.rows(connection, "SELECT hidden_content FROM memo WHERE id=941").getFirst());
                assertEquals(java.util.List.of("0"), SchemaSnapshot.rows(connection, "SELECT hidden_content FROM thought WHERE id=942").getFirst());
                assertEquals(java.util.List.of("not_started", "0", "0"),
                        SchemaSnapshot.rows(connection, "SELECT status,total_progress,current_progress FROM movie WHERE id=943").getFirst());
                assertEquals(java.util.List.of("not_started", "0", "0"),
                        SchemaSnapshot.rows(connection, "SELECT status,total_progress,current_progress FROM read_record WHERE id=949").getFirst());
                assertEquals(java.util.List.of("0", "1", "0"),
                        SchemaSnapshot.rows(connection, "SELECT is_top,is_public,sort_order FROM honor_record WHERE id=944").getFirst());
                assertEquals(java.util.List.of("0"), SchemaSnapshot.rows(connection, "SELECT auto_renew FROM membership_record WHERE id=945").getFirst());
                assertEquals(java.util.List.of("not_started"), SchemaSnapshot.rows(connection, "SELECT status FROM goal WHERE id=946").getFirst());
                assertEquals(java.util.List.of("0"), SchemaSnapshot.rows(connection, "SELECT sort_order FROM honor_category WHERE id=947").getFirst());
                assertEquals(java.util.List.of("0", "0"), SchemaSnapshot.rows(connection, "SELECT category_type,sort_order FROM wardrobe_category WHERE id=948").getFirst());
                assertEquals(java.util.List.of("0"), SchemaSnapshot.rows(connection, "SELECT sort_order FROM task WHERE id=950").getFirst());
                assertEquals(java.util.List.of("1", "0"),
                        SchemaSnapshot.rows(connection, "SELECT is_default FROM sys_dict_data WHERE dict_code IN (951,952) ORDER BY dict_code").stream()
                                .map(row -> row.getFirst()).toList());
                assertEquals(java.util.List.of("1", "0", "0", "1"),
                        SchemaSnapshot.rows(connection, "SELECT is_default,is_readonly FROM user_dict_data WHERE id IN (953,954) ORDER BY id").stream()
                                .flatMap(row -> row.stream()).toList());
                try (var statement = connection.createStatement()) {
                    assertThrows(Exception.class, () -> statement.execute(
                            "INSERT INTO notification_delivery(id,dedup_key,user_id,biz_type,channel,status,payload_ciphertext) VALUES(903,'dup',1,'LEETCODE_REMINDER','FEISHU','PENDING','x')"));
                    assertThrows(Exception.class, () -> statement.execute(
                            "INSERT INTO device(id,name,user_id,source_platform,source_order_id,source_item_id,source_unit_index) VALUES(915,'C',1,'jd','order-1','line-1',1)"));
                    assertThrows(Exception.class, () -> statement.execute("INSERT INTO user_quick_nav(id,user_id,menu_id) VALUES(923,1,10)"));
                    assertThrows(Exception.class, () -> statement.execute("INSERT INTO user_secondary_lock_menu(id,user_id,menu_id) VALUES(933,1,10)"));
                    assertThrows(Exception.class, () -> statement.execute("UPDATE memo SET hidden_content=NULL WHERE id=941"));
                    assertThrows(Exception.class, () -> statement.execute("UPDATE sys_dict_data SET is_default=NULL WHERE dict_code=951"));
                    statement.execute("""
                            INSERT INTO storage_object(id,bucket,object_key,sha256,file_size,content_type,is_deleted) VALUES
                              (961,'b','k','hash',1,'image/png',0),
                              (962,'b','k','hash',1,'image/png',1)
                            """);
                    assertThrows(Exception.class, () -> statement.execute(
                            "INSERT INTO storage_object(id,bucket,object_key,sha256,file_size,content_type,is_deleted) VALUES(963,'b','k','hash',1,'image/png',0)"));
                }
            }
        }
    }
}
