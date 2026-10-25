package top.aiolife.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import top.aiolife.core.resq.PageResp;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.mapper.ExportCoordinatorMapper;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.pojo.req.ExportCreateReq;
import top.aiolife.sso.mapper.UserMapper;

import java.util.List;
import java.util.UUID;

/** 所有状态迁移在短事务中完成；不在事务内生成或传输文件。 */
@Service
@RequiredArgsConstructor
public class ExportJobService {
    private final ExportJobMapper jobs;
    private final ExportCoordinatorMapper coordinator;
    private final UserMapper users;
    private final ExportDatasetRegistry registry;
    private final ExportScope scope;
    private final ExportAccessGuard access;
    private final ExportQuotaService quota;
    private final ExportAuthorizationService authorization;
    private final ExportAuditRecorder audit;
    private final ExportProperties properties;

    public ExportJobEntity owned(long userId, long jobId) {
        ExportJobEntity job = jobs.current(jobId);
        if (job == null || !job.getUserId().equals(userId)) throw ExportException.missing();
        return job;
    }

    public PageResp<ExportJobEntity> history(long userId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数无效");
        var result =
                jobs.selectPage(
                        new Page<>(page, size),
                        new LambdaQueryWrapper<ExportJobEntity>()
                                .eq(ExportJobEntity::getUserId, userId)
                                .orderByDesc(
                                        ExportJobEntity::getCreateTime, ExportJobEntity::getId));
        return new PageResp<>(result.getRecords(), result.getTotal());
    }

    @Transactional
    public ExportJobEntity create(long userId, ExportCreateReq req, String key, String token) {
        if (key == null || !key.matches("[a-zA-Z0-9-]{16,64}"))
            throw new IllegalArgumentException("幂等键无效");
        lockCoordinator();
        if (users.selectForAuthUpdate(userId) == null) throw ExportException.denied();
        var datasets = registry.resolve(req.getDatasetKeys());
        String hash = ExportHash.of(req.getCatalogVersion() + ":" + scope.encode(datasets));
        var existing =
                jobs.selectOne(
                        new LambdaQueryWrapper<ExportJobEntity>()
                                .eq(ExportJobEntity::getUserId, userId)
                                .eq(ExportJobEntity::getRequestKey, key));
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash))
                throw new ExportException(
                        HttpStatus.CONFLICT, "EXPORT_IDEMPOTENCY_CONFLICT", "幂等键已经用于不同的导出范围");
            audit.record(userId, existing.getId(), "REQUEST_REPLAYED", "OK", null);
            return existing;
        }
        if (!registry.version().equals(req.getCatalogVersion()))
            throw new ExportException(
                    HttpStatus.CONFLICT, "EXPORT_SCOPE_CHANGED", "数据目录已更新，请刷新后重新申请");
        access.check(userId, datasets, true);
        List<ExportJobEntity> active = jobs.active();
        if (active.stream().anyMatch(j -> j.getUserId().equals(userId)))
            throw new ExportException(
                    HttpStatus.CONFLICT, "EXPORT_ACTIVE_JOB_EXISTS", "已有导出任务正在处理");
        if (active.size() >= properties.getMaxQueued())
            throw new ExportException(
                    HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_QUEUE_FULL", "导出队列已满，请稍后重试");
        var acceptedAt = ExportTime.now();
        String quotaMonth = ExportTime.month(acceptedAt);
        quota.requireRemaining(userId, quotaMonth);
        var grant = authorization.consume(userId, token, "CREATE", null, datasets);
        ExportJobEntity job = new ExportJobEntity();
        job.fillCreateCommonField(userId);
        job.setCreateTime(acceptedAt);
        job.setUserId(userId);
        job.setRequestKey(key);
        job.setRequestHash(hash);
        job.setScopeJson(scope.encode(datasets));
        job.setSessionHash(grant.getSessionHash());
        job.setSecurityHash(grant.getSecurityHash());
        job.setQuotaMonth(quotaMonth);
        job.setQuotaCharged(1);
        job.setStatus(ExportStatus.QUEUED);
        job.setPhase("QUEUED");
        job.setAttempt(0);
        job.setCancelRequested(0);
        job.setDeadline(ExportTime.now().plusMinutes(properties.getMaxMinutes()));
        job.setArtifactStatus("NONE");
        job.setDownloadCount(0);
        job.setRowCount(0);
        jobs.insert(job);
        audit.record(userId, job.getId(), "REQUEST_ACCEPTED", "OK", job.getScopeJson());
        audit.record(userId, job.getId(), "QUOTA_RESERVED", "OK", job.getQuotaMonth());
        return job;
    }

    @Transactional
    public ExportJobEntity claim() {
        lockCoordinator();
        List<ExportJobEntity> active = jobs.active();
        var now = ExportTime.now();
        for (var job : active) {
            if (!job.getDeadline().isAfter(now)
                    || ExportStatus.RUNNING.equals(job.getStatus())
                            && job.getLeaseUntil().isBefore(now)) {
                if (job.getCancelRequested() != 0) {
                    terminate(
                            job,
                            ExportStatus.CANCELLED,
                            "USER_CANCELLED",
                            job.getStartedAt() == null);
                } else if (job.getAttempt() < 3
                        && job.getDeadline().isAfter(now)
                        && job.getCancelRequested() == 0) {
                    job.setStatus(ExportStatus.QUEUED);
                    job.setPhase("QUEUED");
                    jobs.updateById(job);
                    audit.record(
                            job.getUserId(), job.getId(), "JOB_RETRYING", "LEASE_EXPIRED", null);
                } else terminate(job, ExportStatus.FAILED, "EXPORT_TIMEOUT", true);
            }
        }
        active = jobs.active();
        if (active.stream().filter(j -> ExportStatus.RUNNING.equals(j.getStatus())).count()
                >= properties.getMaxConcurrent()) return null;
        var job =
                active.stream()
                        .filter(j -> ExportStatus.QUEUED.equals(j.getStatus()))
                        .findFirst()
                        .orElse(null);
        if (job == null) return null;
        job.setStatus(ExportStatus.RUNNING);
        job.setPhase("CAPTURING");
        job.setAttempt(job.getAttempt() + 1);
        job.setLeaseToken(UUID.randomUUID().toString());
        job.setLeaseUntil(now.plusSeconds(90));
        if (job.getStartedAt() == null) job.setStartedAt(now);
        jobs.updateById(job);
        audit.record(
                job.getUserId(),
                job.getId(),
                "JOB_STARTED",
                "OK",
                String.valueOf(job.getAttempt()));
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void checkpoint(ExportJobEntity attempt) {
        var current = jobs.current(attempt.getId());
        if (current == null
                || !ExportStatus.RUNNING.equals(current.getStatus())
                || current.getCancelRequested() != 0
                || !attempt.getLeaseToken().equals(current.getLeaseToken())
                || !current.getLeaseUntil().isAfter(ExportTime.now())
                || !current.getDeadline().isAfter(ExportTime.now()))
            throw new ExportException(HttpStatus.CONFLICT, "EXPORT_INTERRUPTED", "导出已中止");
        if (!attempt.getRequestHash()
                .equals(ExportHash.of(registry.version() + ":" + attempt.getScopeJson())))
            throw ExportException.denied();
        if (!access.sessionActive(attempt.getUserId(), attempt.getSessionHash())
                || !attempt.getSecurityHash()
                        .equals(ExportAccessGuard.securityHash(access.user(attempt.getUserId()))))
            throw ExportException.denied();
        access.check(attempt.getUserId(), scope.decode(attempt.getScopeJson()), true);
    }

    @Transactional
    public void finish(
            ExportJobEntity attempt,
            String objectKey,
            String fileName,
            String contentType,
            long bytes,
            String sha256,
            int rows,
            String summary) {
        lockCoordinator();
        var job = jobs.lock(attempt.getId());
        checkpoint(attempt);
        job.setStatus(ExportStatus.SUCCEEDED);
        job.setPhase("DONE");
        job.setFinishedAt(ExportTime.now());
        job.setObjectKey(objectKey);
        job.setFileName(fileName);
        job.setContentType(contentType);
        job.setFileBytes(bytes);
        job.setSha256(sha256);
        job.setRowCount(rows);
        job.setSummaryJson(summary);
        job.setArtifactStatus("AVAILABLE");
        job.setExpiresAt(ExportTime.now().plusHours(properties.getArtifactHours()));
        jobs.updateById(job);
        audit.record(
                job.getUserId(),
                job.getId(),
                "JOB_SUCCEEDED",
                "OK",
                "rows=" + rows + ",bytes=" + bytes);
        audit.record(job.getUserId(), job.getId(), "QUOTA_CONSUMED", "OK", job.getQuotaMonth());
    }

    @Transactional
    public void fail(ExportJobEntity attempt, String code) {
        lockCoordinator();
        var job = jobs.lock(attempt.getId());
        if (job == null
                || !ExportStatus.RUNNING.equals(job.getStatus())
                || !attempt.getLeaseToken().equals(job.getLeaseToken())) return;
        if (job.getCancelRequested() != 0)
            terminate(job, ExportStatus.CANCELLED, "USER_CANCELLED", false);
        else terminate(job, ExportStatus.FAILED, code, true);
    }

    @Transactional
    public void cancel(long userId, long id) {
        lockCoordinator();
        var job = jobs.lock(id);
        if (job == null || !job.getUserId().equals(userId)) throw ExportException.missing();
        if (!ExportStatus.ACTIVE.contains(job.getStatus())) return;
        audit.record(userId, id, "CANCEL_REQUESTED", "OK", null);
        if (ExportStatus.QUEUED.equals(job.getStatus()))
            terminate(job, ExportStatus.CANCELLED, "USER_CANCELLED", job.getStartedAt() == null);
        else {
            job.setCancelRequested(1);
            jobs.updateById(job);
        }
    }

    @Transactional
    public void revokeArtifact(long userId, long id, String state) {
        var job = jobs.lock(id);
        if (job == null || !job.getUserId().equals(userId)) throw ExportException.missing();
        if (!"AVAILABLE".equals(job.getArtifactStatus())) return;
        job.setArtifactStatus(state);
        jobs.updateById(job);
        audit.record(userId, id, "ARTIFACT_" + state, "OK", null);
    }

    private void terminate(ExportJobEntity job, String state, String code, boolean refund) {
        job.setStatus(state);
        job.setPhase("DONE");
        job.setErrorCode(code);
        job.setFinishedAt(ExportTime.now());
        if (refund) job.setQuotaCharged(0);
        jobs.updateById(job);
        audit.record(job.getUserId(), job.getId(), "JOB_" + state, code, null);
        audit.record(
                job.getUserId(),
                job.getId(),
                refund ? "QUOTA_RELEASED" : "QUOTA_CONSUMED",
                "OK",
                job.getQuotaMonth());
    }

    private void lockCoordinator() {
        if (coordinator.lock() == null) throw ExportException.unavailable();
    }
}
