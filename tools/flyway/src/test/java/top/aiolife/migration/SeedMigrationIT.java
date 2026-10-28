package top.aiolife.migration;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 固定历史种子区间的回归，未来业务数据迁移不应被强制要求“不改任何数据”。 */
class SeedMigrationIT {
    private static final String FIX = "V20261028_06__restore_scheduled_jobs_menu.sql";
    @ParameterizedTest @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.40"})
    void 修复菜单并保留历史种子的人工配置与软删除(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database();
            if (mysql.mysql57) {
                mysql.baselineFixture(db);
                mysql.configuration(db).callbacks(new org.flywaydb.core.api.callback.Callback[0])
                        .baselineVersion("20261028.00").load().baseline();
            }
            mysql.configuration(db).target("20261028.05.023").load().migrate();
            assertEquals("0", mysql.scalar(db, "SELECT COUNT(*) FROM sys_menu WHERE name='ScheduledJobAdmin'"));
            mysql.sql(db, "UPDATE sys_menu SET meta=JSON_OBJECT('title','自定义菜单'),web_is_enabled=0,sort_order=700 WHERE id=1001; UPDATE system_config SET config_value='[42]',is_deleted=1 WHERE config_key='feedback.notify_admin_ids'; UPDATE membership_provider SET name='custom provider',is_enabled=0,sort_order=700 WHERE id=195001; UPDATE membership_provider SET name='deleted provider',is_enabled=0,sort_order=701,is_deleted=1 WHERE id=195002; UPDATE cbti_personality SET name='custom personality',is_deleted=1 WHERE code='SUDO'; UPDATE honor_category SET name='custom honor',is_deleted=1 WHERE id=1; UPDATE wardrobe_category SET name='custom wardrobe',is_deleted=1 WHERE id=1; UPDATE user_dict_data SET dict_label='custom payment',is_enabled=0,is_deleted=1 WHERE user_id=0 AND dict_type='pay_type' AND dict_value='1'; UPDATE sys_dict_type SET is_deleted=1 WHERE dict_type='bank'; INSERT INTO user_dict_data(id,user_id,dict_type,dict_label,dict_value,is_enabled,icon,color,update_time) VALUES(2095833545677201409,0,'exercise_type','custom exercise','custom',0,'custom-icon','#abcdef','2026-01-02 03:04:05')");
            var before = mysql.data(db);
            // 重放固定历史种子区间，验证保留策略；生产 baseline 入口另由 FlywayIT 验证。
            mysql.sql(db, "DROP TABLE flyway_schema_history");
            mysql.configuration(db).callbacks(new org.flywaydb.core.api.callback.Callback[0])
                    .baselineVersion("20261028.04").load().baseline();
            mysql.configuration(db).target("20261028.06").load().migrate();
            assertEquals("1912", mysql.scalar(db, "SELECT id FROM sys_menu WHERE name='ScheduledJobAdmin'"));
            var after = mysql.data(db);
            after.put("sys_menu", after.get("sys_menu").stream().filter(row -> !row.contains("\"ScheduledJobAdmin\"")).toList());
            assertEquals(before, after, "Only the missing scheduled-job menu may be added");
            assertEquals(0, mysql.configuration(db).target("20261028.06").load().migrate().migrationsExecuted);
            mysql.sql(db, "UPDATE sys_menu SET meta=JSON_OBJECT('title','custom scheduled'),web_is_enabled=0,sort_order=999,is_deleted=1 WHERE name='ScheduledJobAdmin'");
            before = mysql.data(db); mysql.migration(db, FIX); assertEquals(before, mysql.data(db));
            // 旧库可能在其他 ID 已有同名或同路径菜单；保留，不迁移 ID、不复活软删除行。
            mysql.sql(db, "UPDATE sys_menu SET id=2912,path='/custom/scheduled' WHERE id=1912");
            before = mysql.data(db); mysql.migration(db, FIX); assertEquals(before, mysql.data(db));
            mysql.sql(db, "UPDATE sys_menu SET name='CustomScheduled',path='/system/scheduled-jobs' WHERE id=2912");
            before = mysql.data(db); mysql.migration(db, FIX); assertEquals(before, mysql.data(db));
            mysql.sql(db, "DELETE FROM sys_menu WHERE id=2912; INSERT INTO sys_menu(id,name,path) VALUES(1912,'Unrelated','/unrelated')");
            before = mysql.data(db);
            var conflict = assertThrows(Exception.class, () -> mysql.migration(db, FIX));
            assertTrue(conflict.getMessage().contains("1062"));
            assertEquals(before, mysql.data(db));
            mysql.sql(db, "DELETE FROM sys_menu WHERE id=1912"); mysql.migration(db, FIX);
            assertEquals("1912", mysql.scalar(db, "SELECT id FROM sys_menu WHERE name='ScheduledJobAdmin'"));            // 反向历史冲突：1908 属于定时任务，对象存储尚未创建；不改 ID 以免破坏引用。
            mysql.sql(db, "DELETE FROM sys_menu WHERE name='StorageAdmin'; UPDATE sys_menu SET id=1908 WHERE name='ScheduledJobAdmin'");
            var scheduled = mysql.rows(db, "SELECT * FROM sys_menu WHERE name='ScheduledJobAdmin'");
            mysql.migration(db, FIX);
            assertEquals(scheduled, mysql.rows(db, "SELECT * FROM sys_menu WHERE name='ScheduledJobAdmin'"));
            assertEquals("1913", mysql.scalar(db, "SELECT id FROM sys_menu WHERE name='StorageAdmin'"));
            mysql.sql(db, "UPDATE sys_menu SET meta=JSON_OBJECT('title','custom storage'),is_deleted=1 WHERE id=1913");
            before = mysql.data(db); mysql.migration(db, FIX); assertEquals(before, mysql.data(db));
            mysql.sql(db, "DELETE FROM sys_menu WHERE id=1913; INSERT INTO sys_menu(id,name,path) VALUES(1913,'UnrelatedStorage','/unrelated-storage')");
            before = mysql.data(db);
            conflict = assertThrows(Exception.class, () -> mysql.migration(db, FIX));
            assertTrue(conflict.getMessage().contains("1062")); assertEquals(before, mysql.data(db));

        }
    }
}
