package top.aiolife.record.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.ImageImportTaskMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoCoverWorker {
    private final ImageImportTaskMapper tasks;
    private final VideoCoverDownloader downloader;
    private final VideoCoverService service;

    public int runBatch() {
        int processed = 0;
        for (var task : tasks.due()) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (tasks.claim(task.getId(), token) != 1) continue;
            if (task.getAttempts() >= 5) {
                service.failed(task.getId(), token, "IMAGE_WORKER_INTERRUPTED");
                processed++; continue;
            }
            try { service.complete(task.getId(), token, downloader.download(task.getSourceUrl())); }
            catch (Exception e) {
                // 不记录来源 URL，避免将查询参数中的敏感信息写入日志。
                log.warn("视频封面导入失败 taskId={}, cause={}", task.getId(), e.getClass().getSimpleName());
                service.failed(task.getId(), token, e instanceof IllegalArgumentException ? "INVALID_IMAGE_SOURCE" : "IMAGE_IMPORT_FAILED");
            }
            processed++;
        }
        return processed;
    }
}
