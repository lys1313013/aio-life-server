package top.aiolife.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CurrentSchemaTest {
    @Test
    void table_采集表包含完整来源定位且不重复已有列() throws IOException {
        for (String table : List.of("device", "wardrobe_item")) {
            String sql = CurrentSchema.table(table);
            for (String column : List.of("source_platform", "source_order_id", "source_product_id",
                    "source_item_id", "source_unit_index")) {
                long definitions = Pattern.compile("(?m)^\\s*`" + column + "`\\s")
                        .matcher(sql).results().count();
                assertEquals(1, definitions, table + "." + column);
            }
            assertTrue(sql.toLowerCase(Locale.ROOT).contains(
                    "`source_item_id` varchar(128) character set utf8mb4 collate utf8mb4_bin"));
            assertTrue(sql.toLowerCase(Locale.ROOT).contains("`source_unit_index` int default null"));
            assertTrue(sql.indexOf("`source_product_id`") < sql.indexOf("`source_item_id`"));
            assertTrue(sql.indexOf("`source_item_id`") < sql.indexOf("`source_unit_index`"));
        }
    }

    @Test
    void table_新增来源字段后仍应用公共字段迁移() throws IOException {
        String wardrobe = CurrentSchema.table("wardrobe_item");
        assertTrue(wardrobe.contains("`remark`"));
        assertFalse(wardrobe.contains("`memo`"));

        String dictionary = CurrentSchema.table("sys_dict_data");
        assertTrue(dictionary.contains("`sort_order`"));
        assertTrue(dictionary.contains("`is_enabled` TINYINT DEFAULT 1"));
        assertFalse(dictionary.contains("`dict_sort`"));
        assertFalse(dictionary.contains("`status`"));
        assertFalse(dictionary.contains("`source_item_id`"));
    }

    @Test
    void h2Table_保留新增列且只移除表选项() throws IOException {
        String sql = CurrentSchema.h2Table("device");
        assertTrue(sql.contains("`source_item_id`"));
        assertTrue(sql.contains("`source_unit_index`"));
        assertTrue(sql.contains("PRIMARY KEY (`id`)"));
        assertFalse(sql.contains("ENGINE="));
    }

    @Test
    void table_无条件建表语句同样属于当前结构() throws IOException {
        String sql = CurrentSchema.table("device_expense");
        assertTrue(sql.startsWith("CREATE TABLE `device_expense`"));
        assertTrue(sql.contains("`expense_id` BIGINT NOT NULL"));
    }
}
