package top.aiolife.export.writer;

import org.apache.poi.ss.usermodel.Cell;

import top.aiolife.export.dataset.ExportColumn;

import java.math.BigDecimal;

/** 内容永不当作公式。高精度数值退回文本，避免 Excel 的15位精度限制。 */
public final class ExportCellWriter {
    private ExportCellWriter() {}

    public static void write(Cell cell, ExportColumn column, Object value) {
        if (value == null) return;
        String text = String.valueOf(value);
        if (!column.type().equals("TEXT") && value instanceof Number) {
            BigDecimal number = new BigDecimal(text);
            double numeric = number.doubleValue();
            boolean normal = number.signum() == 0 || Math.abs(numeric) >= Double.MIN_NORMAL;
            if (number.precision() <= 15
                    && normal
                    && Double.isFinite(numeric)
                    && BigDecimal.valueOf(numeric).compareTo(number) == 0) {
                cell.setCellValue(numeric);
                return;
            }
        }
        cell.setCellValue(text);
    }

    public static boolean xmlSafe(String text) {
        return text.codePoints()
                .allMatch(
                        c ->
                                c == 9
                                        || c == 10
                                        || c == 13
                                        || c >= 32 && c <= 0xD7FF
                                        || c >= 0xE000 && c <= 0xFFFD
                                        || c >= 0x10000 && c <= 0x10FFFF);
    }
}
