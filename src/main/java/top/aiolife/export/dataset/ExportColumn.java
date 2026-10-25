package top.aiolife.export.dataset;

import java.util.Set;

/** 稳定字段名、用户可读标题及 Excel 类型。 */
public record ExportColumn(String key, String label, String type) {
    public ExportColumn {
        if (key == null
                || !key.matches("[a-zA-Z][a-zA-Z0-9_]*")
                || label == null
                || !Set.of("TEXT", "NUMBER", "DECIMAL").contains(type)) {
            throw new IllegalArgumentException("Invalid export column");
        }
    }
}
