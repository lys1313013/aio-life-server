package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportColumn;
import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.service.ExportScope;
import top.aiolife.export.service.ExportSnapshotService.Snapshot;
import top.aiolife.export.service.ExportTime;
import top.aiolife.export.storage.ExportWorkspace;
import top.aiolife.export.writer.ExcelExportWriter;
import top.aiolife.export.writer.ExportCellWriter;
import top.aiolife.export.writer.ExportPoiTemporaryFiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ExcelExportWriterTest {
    @TempDir Path directory;

    @Test
    void 文本ID公式字符串高精度空值和长文本均可无损回读() throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        ExportDatasetRegistry registry = mock(ExportDatasetRegistry.class);
        ExportDataset dataset = mock(ExportDataset.class);
        when(dataset.key()).thenReturn("sample");
        when(dataset.title()).thenReturn("测试数据");
        when(dataset.columns())
                .thenReturn(
                        List.of(
                                new ExportColumn("id", "编号", "TEXT"),
                                new ExportColumn("text", "内容", "TEXT"),
                                new ExportColumn("amount", "金额", "DECIMAL"),
                                new ExportColumn("longtext", "长文", "TEXT"),
                                new ExportColumn("empty", "空字符串", "TEXT"),
                                new ExportColumn("absent", "空值", "TEXT")));
        when(registry.resolve(List.of("sample"))).thenReturn(List.of(dataset));
        ExportScope scope = new ExportScope(registry, json);
        ExportProperties properties = new ExportProperties();
        properties.setTemporaryDirectory(directory.toString());
        var writer = new ExcelExportWriter(json, properties, scope, new ExportPoiTemporaryFiles());
        var job = new ExportJobEntity();
        job.setId(9007199254740993L);
        job.setScopeJson("[\"sample\"]");
        String longtext = "长文本😀".repeat(7000);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "0009007199254740993");
        row.put("text", "=HYPERLINK(\"https://example.invalid\")");
        row.put("amount", new BigDecimal("1234567890123456.78"));
        row.put("longtext", longtext);
        row.put("empty", "");
        row.put("absent", null);
        try (var workspace = new ExportWorkspace(properties)) {
            Files.writeString(workspace.file("sample.jsonl"), json.writeValueAsString(row) + "\n");
            var artifact =
                    writer.write(
                            job,
                            List.of(new Snapshot("sample", 1, ExportTime.now(), ExportTime.now())),
                            workspace,
                            () -> {});
            try (var book = new XSSFWorkbook(artifact.path().toFile())) {
                var exported = book.getSheet("测试数据_1").getRow(1);
                assertEquals("0009007199254740993", exported.getCell(0).getStringCellValue());
                assertEquals(CellType.STRING, exported.getCell(1).getCellType());
                assertEquals("1234567890123456.78", exported.getCell(2).getStringCellValue());
                assertEquals(CellType.BLANK, exported.getCell(5).getCellType());
                StringBuilder joined = new StringBuilder();
                boolean emptyFound = false;
                var special = book.getSheet("特殊值_1");
                for (int i = 1; i <= special.getLastRowNum(); i++) {
                    var item = special.getRow(i);
                    if (item.getCell(2).getStringCellValue().equals("longtext"))
                        joined.append(item.getCell(5).getStringCellValue());
                    if (item.getCell(3).getStringCellValue().equals("EMPTY_STRING"))
                        emptyFound = true;
                }
                assertEquals(longtext, joined.toString());
                assertTrue(emptyFound);
            }
        }
        try (var paths = Files.list(directory)) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void 极小数值不能下溢成零() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var cell = book.createSheet().createRow(0).createCell(0);
            ExportCellWriter.write(
                    cell, new ExportColumn("amount", "金额", "DECIMAL"), new BigDecimal("1E-400"));
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("1E-400", cell.getStringCellValue());
            ExportCellWriter.write(
                    cell, new ExportColumn("amount", "金额", "DECIMAL"), new BigDecimal("12.34"));
            assertEquals(CellType.NUMERIC, cell.getCellType());
            assertEquals(12.34, cell.getNumericCellValue());
        }
    }
}
