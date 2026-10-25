package top.aiolife.export.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.service.ExportScope;
import top.aiolife.export.service.ExportSnapshotService.Snapshot;
import top.aiolife.export.storage.ExportWorkspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 仅负责文件格式；不查询业务、鉴权、扣次或改变任务状态。 */
@Component
@RequiredArgsConstructor
public class ExcelExportWriter {
    private final ObjectMapper json;
    private final ExportProperties properties;
    private final ExportScope scope;
    private final ExportPoiTemporaryFiles temporaryFiles;

    public Artifact write(
            ExportJobEntity job,
            List<Snapshot> snapshots,
            ExportWorkspace workspace,
            Runnable checkpoint)
            throws Exception {
        List<Path> files = new ArrayList<>();
        List<Map<String, Object>> manifest = new ArrayList<>();
        int totalRows = 0;
        var rowReader =
                json.readerFor(new TypeReference<Map<String, Object>>() {})
                        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        try (var ignored = temporaryFiles.use(workspace.directory())) {
            Book book = new Book(job, snapshots);
            try {
                for (ExportDataset dataset : scope.decode(job.getScopeJson())) {
                    checkpoint.run();
                    book.describe(dataset);
                    book.sheet(
                            dataset.title(),
                            dataset.columns().stream()
                                    .map(c -> c.label() + " [" + c.key() + "]")
                                    .toList());
                    try (var reader =
                            Files.newBufferedReader(workspace.file(dataset.key() + ".jsonl"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (book.businessRows >= properties.getWorkbookRows()) {
                                save(book, files, workspace);
                                book.close();
                                book = new Book(job, snapshots);
                                book.describe(dataset);
                            }
                            Map<String, Object> values = rowReader.readValue(line);
                            book.row(dataset, values);
                            totalRows = Math.addExact(totalRows, 1);
                            if (totalRows % 500 == 0) {
                                checkpoint.run();
                                workspace.checkSize();
                            }
                        }
                    }
                }
                save(book, files, workspace);
            } finally {
                book.close();
            }
        }
        for (Path file : files)
            manifest.add(
                    Map.of(
                            "file",
                            file.getFileName().toString(),
                            "bytes",
                            Files.size(file),
                            "sha256",
                            digest(file)));
        String summary =
                json.writeValueAsString(
                        Map.of("datasets", snapshots, "files", manifest, "rows", totalRows));
        Path result = files.getFirst();
        String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (files.size() > 1) {
            result = workspace.file("data-export.zip");
            contentType = "application/zip";
            try (var zip = new ZipOutputStream(Files.newOutputStream(result))) {
                for (Path file : files) {
                    checkpoint.run();
                    zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(summary.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        workspace.checkSize();
        if (Files.size(result) > properties.getMaxBytes()) throw limit();
        return new Artifact(result, contentType, digest(result), totalRows, summary);
    }

    private void save(Book book, List<Path> files, ExportWorkspace workspace) throws IOException {
        Path file = workspace.file("data-export-" + (files.size() + 1) + ".xlsx");
        try (var output = Files.newOutputStream(file)) {
            book.workbook.write(output);
        }
        files.add(file);
        workspace.checkSize();
        long size = 0;
        for (Path part : files) size += Files.size(part);
        if (size > properties.getMaxBytes()) throw limit();
    }

    public static String digest(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private ExportException limit() {
        return new ExportException(
                HttpStatus.PAYLOAD_TOO_LARGE, "EXPORT_RESOURCE_LIMIT", "导出文件超过预算，请缩小范围");
    }

    public record Artifact(
            Path path, String contentType, String sha256, int rows, String summary) {}

    private final class Book implements AutoCloseable {
        private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        private final Map<String, Sheet> sheets = new LinkedHashMap<>();
        private final Map<String, Integer> parts = new HashMap<>();
        private int businessRows;

        Book(ExportJobEntity job, List<Snapshot> snapshots) {
            workbook.setCompressTempFiles(true);
            append("导出说明", List.of("项目", "说明"), List.of("任务ID", job.getId().toString()));
            append("导出说明", List.of("项目", "说明"), List.of("时区", "Asia/Shanghai"));
            append(
                    "导出说明",
                    List.of("项目", "说明"),
                    List.of("范围摘要", job.getRequestHash() == null ? "" : job.getRequestHash()));
            append("导出说明", List.of("项目", "说明"), List.of("数据集清单", job.getScopeJson()));
            append(
                    "导出说明",
                    List.of("项目", "说明"),
                    List.of("范围", "标准个人数据；不含认证凭据、密码库秘密、完整卡号、附件原件和已删除数据"));
            append("导出说明", List.of("项目", "说明"), List.of("一致性", "MySQL同一读取事务；其他数据源按各自读取区间，非跨源时点备份"));
            append(
                    "导出说明",
                    List.of("项目", "说明"),
                    List.of("空值和长文本", "空白默认NULL；空字符串和分片原值见特殊值表，按片段序号重组"));
            for (Snapshot item : snapshots)
                append(
                        "数据目录",
                        List.of("数据集", "总行数", "读取开始", "读取结束"),
                        List.of(
                                item.key(),
                                item.rows(),
                                item.readStartedAt().toString(),
                                item.readFinishedAt().toString()));
        }

        void describe(ExportDataset dataset) {
            for (var c : dataset.columns())
                append(
                        "字段说明",
                        List.of("数据集", "字段", "含义", "类型"),
                        List.of(dataset.key(), c.key(), c.label(), c.type()));
        }

        Sheet sheet(String key, List<String> headers) {
            Sheet current = sheets.get(key);
            if (current != null && current.getLastRowNum() < properties.getSheetRows())
                return current;
            int part = parts.merge(key, 1, Integer::sum);
            String name = key.replaceAll("[\\[\\]:*?/\\\\]", "_");
            if (name.length() > 24) name = name.substring(0, 24);
            Sheet created = workbook.createSheet(name + "_" + part);
            var header = created.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
                created.setColumnWidth(i, 24 * 256);
            }
            created.createFreezePane(0, 1);
            sheets.put(key, created);
            return created;
        }

        void append(String key, List<String> headers, List<?> values) {
            Sheet target = sheet(key, headers);
            var row = target.createRow(target.getLastRowNum() + 1);
            for (int i = 0; i < values.size(); i++)
                row.createCell(i).setCellValue(String.valueOf(values.get(i)));
        }

        void row(ExportDataset dataset, Map<String, Object> values) {
            Sheet target =
                    sheet(
                            dataset.title(),
                            dataset.columns().stream()
                                    .map(c -> c.label() + " [" + c.key() + "]")
                                    .toList());
            var row = target.createRow(target.getLastRowNum() + 1);
            for (int i = 0; i < dataset.columns().size(); i++) {
                var column = dataset.columns().get(i);
                Object value = values.get(column.key());
                var cell = row.createCell(i);
                if (value == null) continue;
                String text = String.valueOf(value);
                if (text.isEmpty()
                        || text.length() > 30000
                        || text.chars().filter(c -> c == '\n').count() > 250
                        || !ExportCellWriter.xmlSafe(text)) {
                    String encoding =
                            text.isEmpty()
                                    ? "EMPTY_STRING"
                                    : ExportCellWriter.xmlSafe(text) ? "TEXT" : "BASE64_UTF16BE";
                    String encoded =
                            encoding.equals("BASE64_UTF16BE")
                                    ? Base64.getEncoder()
                                            .encodeToString(
                                                    text.getBytes(StandardCharsets.UTF_16BE))
                                    : text;
                    int offset = 0;
                    int index = 1;
                    do {
                        int end = Math.min(offset + 30000, encoded.length());
                        if (end < encoded.length()
                                && end > offset
                                && Character.isHighSurrogate(encoded.charAt(end - 1))) end--;
                        int lines = 0;
                        for (int at = offset; at < end; at++)
                            if (encoded.charAt(at) == '\n' && ++lines == 250) {
                                end = at + 1;
                                break;
                            }
                        append(
                                "特殊值",
                                List.of("数据集", "记录ID", "字段", "编码", "片段序号", "内容"),
                                List.of(
                                        dataset.key(),
                                        String.valueOf(values.get("id")),
                                        column.key(),
                                        encoding,
                                        index++,
                                        encoded.substring(offset, end)));
                        offset = end;
                    } while (offset < encoded.length());
                    cell.setCellValue("[特殊值:" + column.key() + "]");
                } else ExportCellWriter.write(cell, column, value);
            }
            businessRows++;
        }

        public void close() throws IOException {
            workbook.close();
        }
    }
}
