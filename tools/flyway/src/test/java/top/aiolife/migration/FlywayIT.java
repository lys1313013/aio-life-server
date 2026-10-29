package top.aiolife.migration;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FlywayIT {
    @Test void 新库与旧库从接入版本升级到当前版本() throws Exception {
        Path report = Path.of(System.getProperty("schema.reportDirectory"));
        Files.createDirectories(report);
        SchemaSnapshot reference = null;
        for (String image : List.of("mysql:8.0.40", "mysql:8.4.2")) {
            String suffix = image.contains("8.4") ? "-mysql84" : "";
            Path adoption = report.resolve("adoption-contract" + suffix + ".json");
            Path current = report.resolve("schema-contract" + suffix + ".json");
            try (var mysql = new IsolatedMysql(image)) {
                String fresh = verifyFreshAndUpgrade(mysql, adoption, current);
                assertEquals("20261028.00.001", mysql.scalar(fresh,
                        "SELECT version FROM flyway_schema_history WHERE type='SQL_BASELINE'"));
                var snapshot = mysql.snapshot(fresh);
                if (reference == null) reference = snapshot;
                assertTrue(reference.differences(snapshot).isEmpty(),
                        "Schema must match across supported MySQL versions");
                assertEquals(27, snapshot.sections().get("checks").size());
                constraints(mysql, fresh);
            }
        }
    }
    @Test void 追加结构迁移仍可完成新库和旧库升级(@TempDir Path temporary) throws Exception {
        Path root = TestProject.copy(temporary);
        Files.writeString(root.resolve("sql/migrations/V99999999_01__future_schema.sql"),
                "CREATE TABLE future_probe (id BIGINT NOT NULL COMMENT '主键', PRIMARY KEY(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='未来迁移测试表';");
        try (var mysql = new IsolatedMysql("mysql:8.0.40", root)) {
            String fresh = verifyFreshAndUpgrade(mysql, root.resolve("adoption.json"), root.resolve("current.json"));
            assertEquals("1", mysql.scalar(fresh, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='future_probe'"));
        }
    }
    /** 接入夹具停在 adoption.version；新库另建，二者分别走各自的真实升级路径。 */
    private String verifyFreshAndUpgrade(IsolatedMysql mysql, Path adoption, Path current) throws Exception {
        String old = mysql.database();
        var initial = mysql.configuration(old).target(System.getProperty("adoption.version")).load();
        initial.migrate();
        try (var connection = mysql.connect(old)) {
            SchemaContract.write(adoption, mysql.root, initial.info().current().getVersion().toString(), connection);
        }
        mysql.sql(old, "DROP TABLE flyway_schema_history");
        String fresh = mysql.database();
        var flyway = mysql.flyway(fresh); flyway.migrate();
        assertEquals("0", mysql.scalar(fresh, "SELECT COUNT(*) FROM user"));
        var history = mysql.rows(fresh, "SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank");
        var data = mysql.data(fresh);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(history, mysql.rows(fresh, "SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank"));
        assertEquals(data, mysql.data(fresh));
        try (var connection = mysql.connect(fresh)) {
            SchemaContract.write(current, mysql.root, flyway.info().current().getVersion().toString(), connection);
            SchemaContract.read(current, mysql.root).check(connection);
        }
        mysql.sql(fresh, "UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='20261028.01'");
        assertThrows(Exception.class, flyway::migrate);
        mysql.sql(fresh, "UPDATE flyway_schema_history SET checksum=checksum-1 WHERE version='20261028.01'");
        assertThrows(Exception.class, () -> mysql.flyway(old).migrate());
        assertThrows(Exception.class, () -> mysql.flyway(old).baseline());
        System.setProperty("schema.contract", adoption.toString());
        try {
            assertThrows(Exception.class, () -> mysql.configuration(old).baselineVersion("1").load().baseline());
            mysql.sql(old, "ALTER TABLE device ADD COLUMN unexpected INT");
            assertThrows(Exception.class, () -> mysql.flyway(old).baseline());
            mysql.sql(old, "ALTER TABLE device DROP COLUMN unexpected");
            mysql.flyway(old).baseline(); mysql.flyway(old).migrate();
            assertEquals(mysql.snapshot(fresh), mysql.snapshot(old));
            assertMenus(mysql, old);
        } finally { System.clearProperty("schema.contract"); }
        assertMenus(mysql, fresh);
        return fresh;
    }
    private void assertMenus(IsolatedMysql mysql, String db) throws Exception {
        assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='Analytics' AND path='/'"));
        for (String name : List.of("StorageAdmin", "BankCardCoverAdmin", "AccessLog", "OperationLog", "ScheduledJobAdmin"))
            assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='" + name + "' AND roles='admin'"));
        assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='ScheduledJobAdmin' AND id=1912 AND web_is_enabled=1 AND mobile_is_enabled=0"));
        assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='StorageAdmin' AND id=1908"));
        assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='OperationLog' AND mobile_is_enabled=0"));
    }
    private void constraints(IsolatedMysql mysql, String db) throws Exception {
        mysql.sql(db, "INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) VALUES(-900002,'fixture','fixture','86','13800138000','fixture-openid')");
        assertSqlError(mysql, db, 1062, "INSERT INTO user(id,username,nickname,phone_country_code,phone) VALUES(-900003,'duplicate','fixture','86','13800138000')");
        assertSqlError(mysql, db, 1062, "INSERT INTO user(id,username,nickname,wechat_openid) VALUES(-900003,'duplicate','fixture','fixture-openid')");
        assertSqlError(mysql, db, 3819, "INSERT INTO user(id,username,nickname,phone_country_code) VALUES(-900003,'invalid','fixture','86')");
        mysql.sql(db, "UPDATE user SET is_deleted=1 WHERE id=-900002; INSERT INTO user(id,username,nickname,phone_country_code,phone,wechat_openid) VALUES(-900003,'reused','fixture','86','13800138000','fixture-openid'); INSERT INTO bank_card(id,user_id,custom_bank_name,card_type,create_user,update_user) VALUES(-900001,-900003,'fixture','debit',-900003,-900003)");
        mysql.sql(db, "INSERT INTO file(id,file_name,biz_type,is_public,create_user,create_time,update_time) VALUES('fixture_avatar','fixture.png','avatar',1,-900003,NOW(),NOW()); UPDATE user SET avatar_file_id='fixture_avatar' WHERE id=-900003");
        assertSqlError(mysql, db, 1451, "DELETE FROM file WHERE id='fixture_avatar'");
    }
    private void assertSqlError(IsolatedMysql mysql, String db, int code, String sql) {
        assertEquals(code, assertThrows(java.sql.SQLException.class, () -> mysql.sql(db, sql)).getErrorCode());
    }
}
