package top.aiolife.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.storage.ExportWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 同一个 MySQL 只读事务捕获所有选中数据，再在事务外进行 Excel 打包和上传。 */
@Service
@RequiredArgsConstructor
public class ExportSnapshotService {
    private final ExportScope scope;
    private final ExportJobService jobs;
    private final ExportProperties properties;
    private final ObjectMapper json;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            timeout = 300,
            rollbackFor = Exception.class)
    public List<Snapshot> capture(ExportJobEntity job, ExportWorkspace workspace)
            throws IOException {
        List<Snapshot> result = new ArrayList<>();
        for (ExportDataset dataset : scope.decode(job.getScopeJson())) {
            jobs.checkpoint(job);
            LocalDateTime started = ExportTime.now();
            Path file = workspace.file(dataset.key() + ".jsonl");
            String cursor = null;
            int count = 0;
            try (var writer = Files.newBufferedWriter(file)) {
                while (true) {
                    jobs.checkpoint(job);
                    List<Map<String, Object>> rows =
                            dataset.readBatch(job.getUserId(), cursor, properties.getBatchSize());
                    if (rows.isEmpty()) break;
                    for (Map<String, Object> row : rows) {
                        // 数值、空值和文本原样序列化到受限工作目录；仅含定义中投影的字段。
                        writer.write(json.writeValueAsString(row));
                        writer.newLine();
                        count = Math.addExact(count, 1);
                    }
                    String next = String.valueOf(rows.getLast().get("id"));
                    if (next.equals("null") || next.equals(cursor))
                        throw new IllegalStateException("Export cursor did not advance");
                    cursor = next;
                    writer.flush();
                    workspace.checkSize();
                    if (rows.size() < properties.getBatchSize()) break;
                }
            }
            result.add(new Snapshot(dataset.key(), count, started, ExportTime.now()));
        }
        return List.copyOf(result);
    }

    public record Snapshot(
            String key, int rows, LocalDateTime readStartedAt, LocalDateTime readFinishedAt) {}
}
