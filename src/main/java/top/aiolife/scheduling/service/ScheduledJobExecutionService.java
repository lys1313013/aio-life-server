package top.aiolife.scheduling.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.scheduling.mapper.ScheduledJobLogMapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;

/** 自动、手动共用互斥入口；数据库唯一触发键防止同一计划时点先后重复执行。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobExecutionService {
    private final ScheduledJobService jobs;
    private final ScheduledJobLogMapper logs;
    private final ScheduledJobRegistry registry;
    private final DistributedLockExecutor locks;
    private final String instanceId = UUID.randomUUID().toString();

    public void execute(ScheduledJobEntity expected, Instant scheduled, Long operator) {
        boolean acquired = locks.tryRun(lockKey(expected.getId()), () -> {
            var current = jobs.require(expected.getId());
            if (scheduled != null && (!Integer.valueOf(1).equals(current.getIsEnabled())
                    || !Objects.equals(expected.getRevision(), current.getRevision()))) return;
            recoverLocked(current.getId());
            var entry = entry(current, scheduled, operator);
            entry.setTriggerKey(scheduled == null ? UUID.randomUUID().toString() : "AUTO:" + scheduled.toEpochMilli());
            entry.setStatus("RUNNING");
            try { logs.insert(entry); } catch (DuplicateKeyException ignored) { return; }
            long started = System.nanoTime();
            String status = "SUCCESS";
            String message = "业务方法执行完成；通知投递结果以通知记录为准";
            try {
                registry.execute(current.getJobCode());
            } catch (Exception exception) {
                // 不把外部响应、账号或密钥写入管理页面；详细堆栈通过日志 ID 关联服务日志。
                log.error("定时任务失败 jobCode={}, logId={}", current.getJobCode(), entry.getId(), exception);
                status = "FAILED";
                message = "执行失败（" + exception.getClass().getSimpleName() + "），请按日志 ID 查看服务日志";
            }
            // 记录失败不能误报为业务失败；遗留 RUNNING 由互斥恢复检查标记为结果未知。
            finish(entry.getId(), status, message, started);
        });
        if (!acquired) skipped(expected, scheduled, operator, "同一任务正在执行，本次跳过");
    }

    public void skipped(ScheduledJobEntity job, Instant scheduled, Long operator, String reason) {
        var entry = entry(job, scheduled, operator);
        entry.setTriggerKey(UUID.randomUUID().toString()); entry.setStatus("SKIPPED");
        entry.setEndTime(entry.getStartTime()); entry.setDurationMs(0L); entry.setMessage(reason);
        logs.insert(entry);
    }

    /** 锁服务等基础设施异常也形成可见记录；数据库自身不可用时由服务日志兜底。 */
    public void infrastructureFailed(ScheduledJobEntity job, Instant scheduled, Long operator) {
        var entry = entry(job, scheduled, operator);
        entry.setTriggerKey(UUID.randomUUID().toString()); entry.setStatus("FAILED");
        entry.setEndTime(entry.getStartTime());
        entry.setMessage("执行基础设施异常，业务结果需核查，请按日志 ID 和任务 ID 查看服务日志");
        logs.insert(entry);
    }

    /** 只有持有同一任务执行锁时才能把遗留 RUNNING 标为中断，不自动重放。 */
    public void recover(Long id) {
        locks.tryRun(lockKey(id), () -> recoverLocked(id));
    }

    private void recoverLocked(Long id) {
        logs.update(null, new LambdaUpdateWrapper<ScheduledJobLogEntity>()
                .eq(ScheduledJobLogEntity::getJobId, id).eq(ScheduledJobLogEntity::getStatus, "RUNNING")
                .set(ScheduledJobLogEntity::getStatus, "INTERRUPTED")
                .set(ScheduledJobLogEntity::getMessage, "执行锁已释放但未记录完成，结果未知，请核查后再手动执行")
                .set(ScheduledJobLogEntity::getEndTime, LocalDateTime.now(ScheduledJobRules.ZONE))
                .set(ScheduledJobLogEntity::getUpdateTime, LocalDateTime.now(ScheduledJobRules.ZONE)));
    }

    private ScheduledJobLogEntity entry(ScheduledJobEntity job, Instant scheduled, Long operator) {
        var entry = new ScheduledJobLogEntity();
        entry.fillCreateCommonField(operator); entry.setJobId(job.getId());
        entry.setTriggerType(scheduled == null ? "MANUAL" : "AUTO");
        entry.setScheduledTime(scheduled == null ? null : LocalDateTime.ofInstant(scheduled, ScheduledJobRules.ZONE));
        entry.setStartTime(LocalDateTime.now(ScheduledJobRules.ZONE)); entry.setInstanceId(instanceId);
        return entry;
    }

    private void finish(Long id, String status, String message, long started) {
        logs.update(null, new LambdaUpdateWrapper<ScheduledJobLogEntity>().eq(ScheduledJobLogEntity::getId, id)
                .eq(ScheduledJobLogEntity::getStatus, "RUNNING")
                .set(ScheduledJobLogEntity::getStatus, status).set(ScheduledJobLogEntity::getMessage, message)
                .set(ScheduledJobLogEntity::getEndTime, LocalDateTime.now(ScheduledJobRules.ZONE))
                .set(ScheduledJobLogEntity::getDurationMs, (System.nanoTime() - started) / 1_000_000)
                .set(ScheduledJobLogEntity::getUpdateTime, LocalDateTime.now(ScheduledJobRules.ZONE)));
    }

    private String lockKey(Long id) { return "scheduled-job:execution:" + id; }
}
