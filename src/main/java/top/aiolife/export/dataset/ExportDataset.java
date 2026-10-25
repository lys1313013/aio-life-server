package top.aiolife.export.dataset;

import java.util.List;
import java.util.Map;

/** 数据源扩展点。所有者由任务传入；游标及批大小只由后台控制。 */
public interface ExportDataset {
    String key();

    String title();

    List<String> menus();

    List<ExportColumn> columns();

    default String source() {
        return "MYSQL";
    }

    default boolean available() {
        return true;
    }

    List<Map<String, Object>> readBatch(long ownerId, String cursor, int limit);
}
