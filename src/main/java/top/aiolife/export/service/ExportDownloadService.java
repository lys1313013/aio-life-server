package top.aiolife.export.service;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.storage.ExportArtifactStore;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportDownloadService {
    private final RedissonClient redisson;
    private final ExportJobMapper jobs;
    private final ExportAccessGuard access;
    private final ExportScope scope;
    private final ExportAuthorizationService authorization;
    private final ExportAuditRecorder audit;
    private final ExportProperties properties;

    public AutoCloseable acquire(long userId) {
        var lock = redisson.getLock("export:download:" + userId);
        if (!lock.tryLock())
            throw new ExportException(HttpStatus.CONFLICT, "EXPORT_DOWNLOAD_ACTIVE", "已有下载正在进行");
        return () -> {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        };
    }

    @Transactional
    public Transfer begin(long userId, long id, String grant) {
        ExportJobEntity job = jobs.lock(id);
        check(userId, job);
        if (job.getDownloadCount() >= properties.getMaxDownloads())
            throw new ExportException(
                    HttpStatus.TOO_MANY_REQUESTS, "EXPORT_DOWNLOAD_LIMIT", "该文件下载次数已用完");
        var consumed =
                authorization.consume(
                        userId, grant, "DOWNLOAD", id, scope.decode(job.getScopeJson()));
        job.setDownloadCount(job.getDownloadCount() + 1);
        jobs.updateById(job);
        String transferId = UUID.randomUUID().toString();
        audit.record(userId, id, "DOWNLOAD_STARTED", "OK", transferId);
        return new Transfer(job, transferId, consumed.getSecurityHash());
    }

    public void check(long userId, ExportJobEntity job) {
        if (job == null || !job.getUserId().equals(userId)) throw ExportException.missing();
        if (!ExportStatus.SUCCEEDED.equals(job.getStatus())
                || !"AVAILABLE".equals(job.getArtifactStatus())
                || job.getExpiresAt() == null
                || !job.getExpiresAt().isAfter(ExportTime.now()))
            throw new ExportException(HttpStatus.GONE, "EXPORT_ARTIFACT_EXPIRED", "导出文件不可用或已过期");
        access.check(userId, scope.decode(job.getScopeJson()), true);
    }

    public void stream(Transfer transfer, ExportArtifactStore store, HttpServletResponse response)
            throws Exception {
        ExportJobEntity job = transfer.job();
        long bytes = 0;
        String event = "DOWNLOAD_INTERRUPTED";
        try (InputStream input = store.open(job.getObjectKey())) {
            response.setContentType(job.getContentType());
            response.setHeader(
                    "Content-Disposition", "attachment; filename=\"" + job.getFileName() + "\"");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLengthLong(job.getFileBytes());
            byte[] buffer = new byte[65536];
            int count;
            long checked = 0;
            while ((count = input.read(buffer)) != -1) {
                if (System.nanoTime() - checked > 2_000_000_000L) {
                    access.requestUser();
                    if (!transfer.securityHash()
                            .equals(ExportAccessGuard.securityHash(access.user(job.getUserId()))))
                        throw ExportException.denied();
                    check(job.getUserId(), jobs.current(job.getId()));
                    checked = System.nanoTime();
                }
                response.getOutputStream().write(buffer, 0, count);
                bytes += count;
            }
            response.getOutputStream().flush();
            if (bytes != job.getFileBytes())
                throw new IllegalStateException("Export stream length mismatch");
            event = "DOWNLOAD_STREAM_COMPLETED";
        } finally {
            try {
                audit.independent(
                        job.getUserId(),
                        job.getId(),
                        event,
                        "DOWNLOAD_STREAM_COMPLETED".equals(event) ? "OK" : "INTERRUPTED",
                        transfer.id() + ",bytes=" + bytes);
            } catch (RuntimeException unavailable) {
                log.error("导出传输结束审计未落库，jobId={}, transferId={}", job.getId(), transfer.id());
            }
        }
    }

    public record Transfer(ExportJobEntity job, String id, String securityHash) {}
}
