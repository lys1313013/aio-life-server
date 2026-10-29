package top.aiolife.migration;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 模拟已登记历史边界、实际缺少来源字段的已有库，只执行新版本修复。 */
class AssetSourceRepairIT {
    private static final String REPAIR = "V20261029_01__repair_asset_purchase_source.sql";
    private static final List<String> TABLES = List.of("device", "wardrobe_item");

    @ParameterizedTest
    @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.40", "mysql:8.4.2"})
    void 已登记基线的缺列库补齐且保留数据并拒绝冲突(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database();
            mysql.configuration(db).target(System.getProperty("adoption.version")).load().migrate();
            var expected = mysql.snapshot(db);
            for (String table : TABLES) {
                mysql.sql(db, "ALTER TABLE `" + table + "` DROP INDEX idx_" + table
                        + "_capture_order,DROP COLUMN source_platform,DROP COLUMN source_order_id,"
                        + "DROP COLUMN source_product_id,DROP COLUMN source_item_id,DROP COLUMN source_unit_index");
                var values = IncrementalMigrationIT.requiredValues(mysql, db, table);
                values.put("id", "101");
                values.put("user_id", "11");
                values.put("name", "'保留设备或衣物'");
                values.put("update_time", "'2026-01-02 03:04:05'");
                IncrementalMigrationIT.insert(mysql, db, table, values);
            }
            mysql.sql(db, "DROP TABLE flyway_schema_history");
            Flyway.configure().dataSource(mysql.url(db), "root", mysql.password)
                    .baselineVersion(System.getProperty("adoption.version")).load().baseline();
            var history = mysql.rows(db, "SELECT version,type,success FROM flyway_schema_history");
            assertEquals(1, mysql.flyway(db).info().pending().length);
            assertEquals(1, mysql.flyway(db).migrate().migrationsExecuted);
            mysql.flyway(db).validate();
            assertEquals(expected, mysql.snapshot(db));
            assertEquals(history, mysql.rows(db,
                    "SELECT version,type,success FROM flyway_schema_history WHERE type='BASELINE'"));
            for (String table : TABLES) {
                assertEquals(List.of(List.of("101", "11", "保留设备或衣物", "2026-01-02 03:04:05")),
                        mysql.rows(db, "SELECT id,user_id,name,update_time FROM `" + table + "`"));
                assertEquals("1", mysql.scalar(db, "SELECT COUNT(*) FROM `" + table
                        + "` WHERE source_platform IS NULL AND source_order_id IS NULL"
                        + " AND source_product_id IS NULL AND source_item_id IS NULL AND source_unit_index IS NULL"));
            }
            var data = mysql.data(db);
            assertEquals(0, mysql.flyway(db).migrate().migrationsExecuted);
            mysql.sql(db, "DROP TABLE flyway_schema_history");
            mysql.migration(db, REPAIR);
            assertEquals(expected, mysql.snapshot(db));
            assertEquals(data, mysql.data(db));
            // 部分完成后可恢复；第二表存在冲突时第一表不得先发生 ALTER。
            mysql.sql(db, "ALTER TABLE device DROP COLUMN source_unit_index;"
                    + " ALTER TABLE wardrobe_item MODIFY source_platform VARCHAR(99) NULL COMMENT '冲突'");
            IncrementalMigrationIT.failureWithoutAlter(mysql, db, REPAIR);
            mysql.sql(db, "ALTER TABLE wardrobe_item DROP INDEX idx_wardrobe_item_capture_order,DROP COLUMN source_platform");
            mysql.migration(db, REPAIR);
            assertEquals(expected, mysql.snapshot(db));
            assertEquals(data, mysql.data(db));
        }
    }
}
