package top.aiolife.record.service;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
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
            long started = System.nanoTime();
            String phase = "DOWNLOAD";
            try {
                byte[] bytes = downloader.download(task.getSourceUrl());
                phase = "STORE";
                service.complete(task.getId(), token, bytes);
                log.info("视频封面任务处理结束 taskId={}, attempt={}, elapsedMs={}",
                        task.getId(), task.getAttempts() + 1, elapsedMillis(started));
            }
            catch (Exception e) {
                String code = failureCode(phase, e);
                // 仅输出受控错误码和异常类型，禁止记录来源 URL、异常原文或请求内容。
                log.warn("视频封面导入失败 taskId={}, attempt={}, phase={}, code={}, cause={}, elapsedMs={}",
                        task.getId(), task.getAttempts() + 1, phase, code,
                        e.getClass().getSimpleName(), elapsedMillis(started));
                service.failed(task.getId(), token, code);
            }
            processed++;
        }
        return processed;
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String failureCode(String phase, Exception exception) {
        if (exception instanceof DataAccessException) return "IMAGE_DATABASE_FAILED";
        if ("STORE".equals(phase)) return "IMAGE_STORAGE_FAILED";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException) return "IMAGE_DNS_FAILED";
            if (cause instanceof SocketTimeoutException) return "IMAGE_DOWNLOAD_TIMEOUT";
            if (cause instanceof ConnectException) return "IMAGE_CONNECT_FAILED";
            if (cause instanceof SSLException) return "IMAGE_TLS_FAILED";
            if (cause instanceof InterruptedIOException) return "IMAGE_DOWNLOAD_TIMEOUT";
        }
        // 下载器的 HTTP 错误只允许固定格式，其他异常消息不能进入日志或数据库。
        String message = exception.getMessage();
        if (exception instanceof IllegalStateException && message != null && message.matches("IMAGE_HTTP_[0-9]{3}"))
            return message;
        if (exception instanceof IllegalArgumentException) return "INVALID_IMAGE_SOURCE";
        return "IMAGE_DOWNLOAD_FAILED";
    }
}
