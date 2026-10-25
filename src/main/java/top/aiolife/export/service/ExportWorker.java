package top.aiolife.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.storage.ExportArtifactStore;
import top.aiolife.export.storage.ExportWorkspace;
import top.aiolife.export.writer.ExcelExportWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 持久化队列驱动；本地线程只执行已获得数据库租约的任务。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportWorker {
    private final ExportProperties properties;
    private final ExportJobService jobs;
    private final ExportJobMapper mapper;
    private final ExportSnapshotService snapshots;
    private final ExcelExportWriter writer;
    private final ExportArtifactStore store;
    private final ExportAuditRecorder audit;
    private final ExportAccessGuard access;
    private String orphanCursor;
    private Long artifactCursor;
    private final Semaphore localSlots = new Semaphore(2);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(2);

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        if (!properties.isEnabled()) return;
        while (localSlots.tryAcquire()) {
            ExportJobEntity job;
            try {
                job = jobs.claim();
            } catch (RuntimeException unavailable) {
                localSlots.release();
                log.error("导出任务领取失败");
                return;
            }
            if (job == null) {
                localSlots.release();
                return;
            }
            executor.submit(
                    () -> {
                        try {
                            run(job);
                        } finally {
                            localSlots.release();
                        }
                    });
        }
    }

    private void run(ExportJobEntity job) {
        String objectKey = "exports/" + job.getId() + "/" + UUID.randomUUID();
        boolean published = false;
        var beat =
                heartbeat.scheduleAtFixedRate(
                        () -> {
                            try {
                                mapper.heartbeat(
                                        job.getId(),
                                        job.getLeaseToken(),
                                        ExportTime.now(),
                                        ExportTime.now().plusSeconds(90));
                            } catch (RuntimeException unavailable) {
                                log.error("导出任务续租失败，jobId={}", job.getId());
                            }
                        },
                        20,
                        20,
                        TimeUnit.SECONDS);
        try (var workspace = new ExportWorkspace(properties)) {
            store.verifyPrivateStorage();
            var captured = snapshots.capture(job, workspace);
            for (var dataset : captured)
                audit.independent(
                        job.getUserId(),
                        job.getId(),
                        "DATASET_FINISHED",
                        "OK",
                        dataset.key() + ",rows=" + dataset.rows());
            var artifact = writer.write(job, captured, workspace, () -> jobs.checkpoint(job));
            jobs.checkpoint(job);
            store.put(objectKey, artifact.path(), artifact.contentType());
            String extension = artifact.contentType().equals("application/zip") ? ".zip" : ".xlsx";
            jobs.finish(
                    job,
                    objectKey,
                    "aio-life-data-" + job.getId() + extension,
                    artifact.contentType(),
                    Files.size(artifact.path()),
                    artifact.sha256(),
                    artifact.rows(),
                    artifact.summary());
            published = true;
        } catch (Exception failure) {
            String code =
                    failure instanceof ExportException error
                            ? error.getErrorCode()
                            : "EXPORT_PROCESSING_FAILED";
            try {
                jobs.fail(job, code);
            } catch (RuntimeException unavailable) {
                log.error("导出任务终态未落库，等待租约恢复，jobId={}", job.getId());
            }
        } finally {
            beat.cancel(false);
            if (!published)
                try {
                    store.delete(objectKey);
                } catch (Exception ignored) {
                    log.error("导出临时对象待清理，jobId={}", job.getId());
                }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        if (!properties.isEnabled()) return;
        try {
            var files =
                    mapper.selectList(
                            new LambdaQueryWrapper<ExportJobEntity>()
                                    .isNotNull(ExportJobEntity::getObjectKey)
                                    .gt(
                                            artifactCursor != null,
                                            ExportJobEntity::getId,
                                            artifactCursor)
                                    .orderByAsc(ExportJobEntity::getId)
                                    .last("LIMIT 100"));
            artifactCursor = files.isEmpty() ? null : files.getLast().getId();
            for (var job : files) {
                boolean expired =
                        job.getExpiresAt() != null && !job.getExpiresAt().isAfter(ExportTime.now());
                boolean deleted = false;
                try {
                    access.user(job.getUserId());
                } catch (ExportException missing) {
                    deleted = true;
                }
                if ("AVAILABLE".equals(job.getArtifactStatus()) && !expired && !deleted) continue;
                jobs.revokeArtifact(
                        job.getUserId(),
                        job.getId(),
                        deleted ? "REVOKED" : expired ? "EXPIRED" : "DELETED");
                try {
                    store.delete(job.getObjectKey());
                    mapper.update(
                            null,
                            new LambdaUpdateWrapper<ExportJobEntity>()
                                    .eq(ExportJobEntity::getId, job.getId())
                                    .eq(ExportJobEntity::getObjectKey, job.getObjectKey())
                                    .set(ExportJobEntity::getObjectKey, null));
                    audit.independent(job.getUserId(), job.getId(), "ARTIFACT_DELETED", "OK", null);
                } catch (Exception unavailable) {
                    audit.independent(
                            job.getUserId(),
                            job.getId(),
                            "CLEANUP_FAILED",
                            "STORAGE_UNAVAILABLE",
                            null);
                }
            }
            cleanOrphans();
        } catch (Exception unavailable) {
            log.error("导出文件清理暂不可用");
        }
    }

    private void cleanOrphans() throws Exception {
        var cutoff = Instant.now().minusSeconds(3600);
        var objects = store.scan(orphanCursor);
        orphanCursor = objects.isEmpty() ? null : objects.getLast().key();
        for (var object : objects) {
            if (object.modifiedAt().isBefore(cutoff)
                    && mapper.selectCount(
                                    new LambdaQueryWrapper<ExportJobEntity>()
                                            .eq(ExportJobEntity::getObjectKey, object.key()))
                            == 0) {
                store.delete(object.key());
                audit.independent(null, null, "ORPHAN_ARTIFACT_DELETED", "OK", null);
            }
        }
        Path root = Path.of(properties.getTemporaryDirectory());
        if (!Files.isDirectory(root)) return;
        try (var directories = Files.list(root)) {
            for (var directory : directories.toList()) {
                if (!directory.getFileName().toString().matches("[0-9a-f-]{36}")
                        || !Files.isDirectory(directory)
                        || !Files.getLastModifiedTime(directory).toInstant().isBefore(cutoff))
                    continue;
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        heartbeat.shutdownNow();
    }
}
