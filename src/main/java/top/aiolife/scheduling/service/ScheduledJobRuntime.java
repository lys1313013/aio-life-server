package top.aiolife.scheduling.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.aiolife.scheduling.mapper.ScheduledJobMapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;

/** 独立的小线程池，防止第三方 API 阻塞现有内部轮询；不提供持久化任务队列。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRuntime {
    private final ScheduledJobMapper mapper;
    private final ScheduledJobService service;
    private final ScheduledJobExecutionService execution;
    private final ScheduledJobRegistry registry;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final ThreadPoolTaskExecutor workers = new ThreadPoolTaskExecutor();
    private final Map<Long, Registration> registrations = new HashMap<>();
    @Value("${aio.life.scheduling.enabled:true}")
    private boolean enabled;
    private volatile boolean ready;

    private record Registration(Integer revision, ScheduledFuture<?> future) { }

    @PostConstruct
    public void initialize() {
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("managed-schedule-");
        scheduler.setRemoveOnCancelPolicy(true); scheduler.initialize();
        workers.setCorePoolSize(2); workers.setMaxPoolSize(2); workers.setQueueCapacity(0);
        workers.setThreadNamePrefix("managed-job-");
        workers.setWaitForTasksToCompleteOnShutdown(true); workers.setAwaitTerminationSeconds(30); workers.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        service.initializeDefaults();
        ready = true;
        refresh();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(ScheduledJobChanged event) {
        // 数据已提交；局部调度异常只记录并由周期刷新重试，不能向客户端伪报保存失败。
        try { refresh(); } catch (Exception exception) { log.error("任务配置已保存，调度刷新待重试", exception); }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public synchronized void refresh() {
        if (!ready || !enabled) return;
        var rows = mapper.selectList(new LambdaQueryWrapper<ScheduledJobEntity>()
                .in(ScheduledJobEntity::getJobCode, registry.codes()));
        var remaining = new HashSet<>(registrations.keySet());
        for (var row : rows) {
            remaining.remove(row.getId());
            execution.recover(row.getId());
            var existing = registrations.get(row.getId());
            if (!Integer.valueOf(1).equals(row.getIsEnabled())) { cancel(row.getId()); continue; }
            if (existing != null && Objects.equals(existing.revision(), row.getRevision()) && !existing.future().isDone()) continue;
            cancel(row.getId());
            ScheduledJobRules.validateCron(row.getCron());
            var trigger = new CronTrigger(row.getCron(), ScheduledJobRules.ZONE);
            var scheduled = new AtomicReference<Instant>();
            var future = scheduler.schedule(() -> dispatch(row, scheduled.get(), null), context -> {
                Instant next = trigger.nextExecution(context);
                scheduled.set(next);
                return next;
            });
            if (future != null) registrations.put(row.getId(), new Registration(row.getRevision(), future));
        }
        remaining.forEach(this::cancel);
    }

    public void runNow(Long id, Long operator) {
        if (!ready || !enabled) throw new IllegalArgumentException("当前服务未开启任务调度");
        dispatch(service.require(id), null, operator);
    }

    private void dispatch(ScheduledJobEntity row, Instant scheduled, Long operator) {
        try {
            workers.execute(() -> {
                try { execution.execute(row, scheduled, operator); }
                catch (Exception exception) {
                    log.error("任务执行基础设施异常 jobId={}", row.getId(), exception);
                    try { execution.infrastructureFailed(row, scheduled, operator); }
                    catch (Exception recordFailure) { log.error("任务异常记录写入失败 jobId={}", row.getId(), recordFailure); }
                }
            });
        } catch (TaskRejectedException exception) {
            if (scheduled == null) throw new IllegalArgumentException("任务执行器忙，请稍后重试");
            execution.skipped(row, scheduled, null, "任务执行器忙，本次跳过");
        }
    }

    private void cancel(Long id) {
        var old = registrations.remove(id);
        if (old != null) old.future().cancel(false);
    }

    @PreDestroy
    public synchronized void close() {
        ready = false;
        registrations.values().forEach(item -> item.future().cancel(false));
        registrations.clear(); scheduler.shutdown(); workers.shutdown();
    }
}
