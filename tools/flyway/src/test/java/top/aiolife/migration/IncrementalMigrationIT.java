package top.aiolife.migration;

import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalMigrationIT {
    static final String ASSET = "V20261028_01__asset_purchase_source.sql";
    static final String FIELDS = "V20261028_02__unify_common_fields.sql";
    static final String ALIGN = "V20261028_03__schema_alignment.sql";
    static final String COMMENTS = "V20261028_04__schema_comments.sql";
    @ParameterizedTest @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.40"})
    void assetAndAlignment(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database(); mysql.baselineFixture(db);
            mysql.sql(db, "INSERT INTO device(id,user_id,name,update_time) VALUES(101,11,'设备','2026-01-02 03:04:05'); INSERT INTO expense(id,amt,exp_type_id,user_id,exp_time) VALUES(301,12.34,21,11,'2026-01-01'); INSERT INTO income(id,amt,inc_type_id,user_id,inc_date) VALUES(401,56.78,22,11,'2026-01-01')");
            mysql.migration(db, ASSET);
            var complete = mysql.snapshot(db); var data = mysql.data(db);
            mysql.migration(db, ASSET); assertEquals(complete, mysql.snapshot(db)); assertEquals(data, mysql.data(db));
            mysql.sql(db, "ALTER TABLE device DROP INDEX idx_device_capture_order,DROP COLUMN source_order_id,DROP COLUMN source_unit_index; ALTER TABLE wardrobe_item DROP COLUMN source_item_id");
            mysql.migration(db, ASSET); assertEquals(complete, mysql.snapshot(db));
            for (String table : List.of("device", "wardrobe_item")) mysql.sql(db, "ALTER TABLE `" + table + "` DROP INDEX idx_" + table + "_capture_order,DROP COLUMN source_platform,DROP COLUMN source_order_id,DROP COLUMN source_product_id,DROP COLUMN source_item_id,DROP COLUMN source_unit_index");
            mysql.migration(db, ASSET); assertEquals(complete, mysql.snapshot(db));
            mysql.sql(db, "ALTER TABLE wardrobe_item MODIFY source_platform VARCHAR(99) NULL COMMENT '冲突'");
            failureWithoutAlter(mysql, db, ASSET);
            mysql.sql(db, "ALTER TABLE wardrobe_item DROP INDEX idx_wardrobe_item_capture_order,DROP COLUMN source_platform"); mysql.migration(db, ASSET);
            mysql.sql(db, "ALTER TABLE expense MODIFY exp_type_id MEDIUMTEXT NOT NULL COMMENT '支出类型ID',MODIFY user_id BIGINT NULL COMMENT '用户id'; ALTER TABLE income MODIFY inc_type_id MEDIUMTEXT NOT NULL COMMENT '收入类型',MODIFY amt DECIMAL(10,2) NULL COMMENT '收入'; ALTER TABLE user_dict_data DROP INDEX idx_user_dict_type,DROP INDEX idx_user_dict_template; ALTER TABLE user_dict_type DROP INDEX idx_user_dict_type_user,MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',MODIFY dict_name VARCHAR(100) NULL DEFAULT '' COMMENT '字典名称',MODIFY dict_type VARCHAR(100) NULL DEFAULT '' COMMENT '字典类型'; ALTER TABLE login_log ADD COLUMN password VARCHAR(255) NULL COMMENT '废弃密码'; ALTER TABLE mail_log ADD COLUMN content TEXT NULL COMMENT '废弃正文'");
            for (String value : List.of("9223372036854775808", "-9223372036854775809", "12x", "12.0", "", " 12", "12\n")) {
                mysql.sql(db, "UPDATE expense SET exp_type_id=" + quote(value)); failureWithoutAlter(mysql, db, ALIGN);
            }
            mysql.sql(db, "UPDATE expense SET exp_type_id='-9223372036854775808'; UPDATE income SET inc_type_id='9223372036854775807'; UPDATE expense SET user_id=NULL");
            failureWithoutAlter(mysql, db, ALIGN);
            mysql.sql(db, "UPDATE expense SET user_id=11; UPDATE income SET amt=NULL"); failureWithoutAlter(mysql, db, ALIGN);
            mysql.sql(db, "UPDATE income SET amt=56.78; ALTER TABLE login_log ADD INDEX conflict(password)"); failureWithoutAlter(mysql, db, ALIGN);
            mysql.sql(db, "ALTER TABLE login_log DROP INDEX conflict; ALTER TABLE login_log ADD COLUMN password_length INT AS (CHAR_LENGTH(password))"); failureWithoutAlter(mysql, db, ALIGN);
            mysql.sql(db, "ALTER TABLE login_log DROP COLUMN password_length; CREATE VIEW password_dependency AS SELECT password FROM login_log"); failureWithoutAlter(mysql, db, ALIGN);
            mysql.sql(db, "DROP VIEW password_dependency");
            var expense = mysql.rows(db, "SELECT id,amt,exp_type_id,user_id,update_time FROM expense");
            mysql.migration(db, ALIGN);
            assertEquals(expense, mysql.rows(db, "SELECT id,amt,exp_type_id,user_id,update_time FROM expense"));
            assertEquals("9223372036854775807", mysql.scalar(db, "SELECT inc_type_id FROM income"));
            var aligned = mysql.snapshot(db); mysql.migration(db, ALIGN); assertEquals(aligned, mysql.snapshot(db));
            mysql.sql(db, "ALTER TABLE user_dict_data DROP INDEX idx_user_dict_template; ALTER TABLE mail_log ADD COLUMN content TEXT NULL COMMENT '废弃正文'");
            mysql.migration(db, ALIGN); assertEquals(aligned, mysql.snapshot(db));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.40"})
    void commonFieldDataAndRecovery(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database(); mysql.baselineFixture(db);
            var matcher = Pattern.compile("CALL aio_unify_field\\('([^']+)', '([^']+)', '([^']+)', '([^']+)', ([01]), 0\\)").matcher(mysql.source(FIELDS));
            List<List<String>> plans = new ArrayList<>();
            while (matcher.find()) plans.add(List.of(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5)));
            assertEquals(23, plans.size());
            for (String table : plans.stream().map(List::getFirst).distinct().toList()) {
                Map<String, String> values = requiredValues(mysql, db, table);
                for (var plan : plans) if (plan.getFirst().equals(table)) values.put(plan.get(1), plan.get(4).equals("1") ? "'0'" : plan.get(2).equals("remark") ? "'迁移备注'" : plan.get(2).contains("enabled") ? "0" : "-7");
                values.put("update_time", "'2026-10-01 01:02:03'"); insert(mysql, db, table, values);
                if (table.contains("dict")) {
                    for (int i = 0; i < 2; i++) {
                        for (String key : List.of("id", "dict_id", "dict_code")) if (values.containsKey(key)) values.put(key, "" + (1002 + i));
                        for (String key : List.of("dict_type", "dict_name", "dict_value", "dict_label")) if (values.containsKey(key)) values.put(key, "'fixture" + i + "'");
                        values.put("status", i == 0 ? "'1'" : "NULL"); insert(mysql, db, table, values);
                    }
                }
            }
            mysql.migration(db, FIELDS);
            for (var plan : plans) {
                String table = plan.getFirst();
                if (plan.get(4).equals("1")) assertEquals("NULL,0,1", mysql.scalar(db, "SELECT GROUP_CONCAT(COALESCE(is_enabled,'NULL') ORDER BY COALESCE(is_enabled,-1)) FROM `" + table + "`"));
                else assertEquals(plan.get(2).equals("remark") ? "迁移备注" : plan.get(2).contains("enabled") ? "0" : "-7", mysql.scalar(db, "SELECT DISTINCT `" + plan.get(2) + "` FROM `" + table + "`"));
                assertEquals("2026-10-01 01:02:03", mysql.scalar(db, "SELECT DISTINCT update_time FROM `" + table + "`"));
            }
            var data = mysql.data(db); var schema = mysql.snapshot(db); mysql.migration(db, FIELDS);
            assertEquals(data, mysql.data(db)); assertEquals(schema, mysql.snapshot(db));
            mysql.sql(db, "ALTER TABLE user_dict_data CHANGE COLUMN is_enabled __aio_legacy_status CHAR(1) NULL DEFAULT '0',ADD COLUMN is_enabled TINYINT NULL COMMENT '字段统一迁移中，禁止业务写入'; UPDATE user_dict_data SET __aio_legacy_status=CASE __aio_legacy_status WHEN '1' THEN '0' WHEN '0' THEN '1' ELSE NULL END,update_time=update_time");
            mysql.migration(db, FIELDS);
            assertEquals(data.get("user_dict_data"), mysql.data(db).get("user_dict_data"));
            mysql.sql(db, "ALTER TABLE sys_menu CHANGE COLUMN sort_order sort INT NOT NULL DEFAULT 0 COMMENT '排序'; ALTER TABLE sys_dict_type CHANGE COLUMN is_enabled status CHAR(1) NULL DEFAULT '0'; UPDATE sys_dict_type SET status='2',update_time=update_time"); failureWithoutAlter(mysql, db, FIELDS);
            mysql.sql(db, "UPDATE sys_dict_type SET status='0',update_time=update_time"); mysql.migration(db, FIELDS);
            mysql.sql(db, "UPDATE sys_dict_type SET is_enabled=2,update_time=update_time"); failureWithoutAlter(mysql, db, FIELDS);
            mysql.sql(db, "UPDATE sys_dict_type SET is_enabled=1,update_time=update_time; ALTER TABLE sys_menu ADD COLUMN sort INT COMMENT '冲突'"); failureWithoutAlter(mysql, db, FIELDS);
            mysql.sql(db, "ALTER TABLE sys_menu DROP COLUMN sort"); mysql.migration(db, FIELDS);
        }
    }
    static Map<String, String> requiredValues(IsolatedMysql mysql, String db, String table) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (var row : mysql.rows(db, "SELECT column_name,data_type,is_nullable,column_default,extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + quote(table) + " ORDER BY ordinal_position")) {
            if (!row.get(2).equals("NO") || row.get(3) != null || !row.get(4).isEmpty()) continue;
            String type = row.get(1);
            values.put(row.getFirst(), type.matches("tinyint|smallint|mediumint|int|bigint|decimal|double|float") ? "10" : type.equals("date") ? "'2026-10-01'" : type.matches("datetime|timestamp") ? "'2026-10-01 01:02:03'" : type.equals("json") ? "'{}'" : "'fixture'");
        }
        return values;
    }
    static void insert(IsolatedMysql mysql, String db, String table, Map<String, String> values) throws Exception {
        mysql.sql(db, "INSERT INTO `" + table + "` (`" + String.join("`,`", values.keySet()) + "`) VALUES (" + String.join(",", values.values()) + ")");
    }
    static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"; }
    static void failureWithoutAlter(IsolatedMysql mysql, String db, String file) throws Exception {
        var before = mysql.snapshot(db);
        var failure = assertThrows(Exception.class, () -> mysql.migration(db, file));
        assertTrue(failure.getMessage().contains("45000"), failure.getMessage());
        assertEquals(before, mysql.snapshot(db), "Rejected migration changed business schema");
    }
}
