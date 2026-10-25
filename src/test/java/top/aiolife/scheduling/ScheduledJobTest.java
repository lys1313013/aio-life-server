package top.aiolife.scheduling;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.service.ILeetcodeService;
import top.aiolife.scheduling.mapper.ScheduledJobMapper;
import top.aiolife.scheduling.mapper.ScheduledJobLogMapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;
import top.aiolife.scheduling.pojo.req.ScheduledJobUpdateReq;
import top.aiolife.scheduling.service.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledJobTest {
    ScheduledJobMapper jobs = mock(ScheduledJobMapper.class);
    ScheduledJobLogMapper logs = mock(ScheduledJobLogMapper.class);
    ILeetcodeService leetcode = mock(ILeetcodeService.class);
    ScheduledJobRegistry registry = new ScheduledJobRegistry(leetcode);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    MockEnvironment environment = new MockEnvironment();
    ScheduledJobService service = new ScheduledJobService(jobs, logs, registry, events, environment);
    DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    ScheduledJobEntity row;

    @BeforeEach void init() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ScheduledJobEntity.class);
        TableInfoHelper.initTableInfo(assistant, ScheduledJobLogEntity.class);
        row = new ScheduledJobEntity(); row.setId(9007199254740993L); row.setJobCode(ScheduledJobRegistry.CHECK);
        row.setCron("0 30 23 * * ?"); row.setZoneId("Asia/Shanghai"); row.setEnabled(true); row.setRevision(0);
        when(jobs.selectById(row.getId())).thenReturn(row);
        ReflectionTestUtils.setField(service, "schedulerEnabled", true);
    }

    @Test void Cron校验拒绝秒级频率非法日期与无未来时间() {
        assertEquals("0 30 23 * * ?", ScheduledJobRules.validateCron(" 0  30 23 * * ? "));
        assertThrows(IllegalArgumentException.class, () -> ScheduledJobRules.validateCron("* * * * * *"));
        assertThrows(IllegalArgumentException.class, () -> ScheduledJobRules.validateCron("0 0 25 * * ?"));
        assertThrows(IllegalArgumentException.class, () -> ScheduledJobRules.validateCron("0 0 0 31 2 ?"));
        assertThrows(IllegalArgumentException.class, () -> ScheduledJobRules.validateCron("-"));
        assertThrows(IllegalArgumentException.class, () -> ScheduledJobRules.validateCron("@daily"));
    }

    @Test void 保存并发冲突不得发布调度事件() {
        var req = new ScheduledJobUpdateReq(); req.setCron(row.getCron()); req.setEnabled(false); req.setRevision(0);
        when(jobs.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.update(row.getId(), req, 1L));
        verifyNoInteractions(events);
        when(jobs.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.update(row.getId(), req, 1L);
        verify(events).publishEvent(new ScheduledJobChanged(row.getId()));
    }

    @Test void 初始化沿用旧Cron并且不覆盖已有配置() {
        environment.setProperty("aio.life.server.corn.leetcodeCheck", "0 0 22 * * ?");
        environment.setProperty("aio.life.server.corn.notifyTodayQuestion", "-");
        when(jobs.selectCount(any())).thenReturn(0L);
        service.initializeDefaults();
        verify(jobs).insert(argThat((ScheduledJobEntity j) -> j.getJobCode().equals(ScheduledJobRegistry.CHECK) && j.getCron().equals("0 0 22 * * ?")));
        verify(jobs).insert(argThat((ScheduledJobEntity j) -> j.getJobCode().equals(ScheduledJobRegistry.DAILY) && !j.getEnabled()));
        clearInvocations(jobs);
        when(jobs.selectCount(any())).thenReturn(1L);
        service.initializeDefaults();
        verify(jobs, never()).insert(any(ScheduledJobEntity.class));
    }

    private ScheduledJobExecutionService execution() {
        when(locks.tryRun(anyString(), any())).thenAnswer(call -> { call.getArgument(1, Runnable.class).run(); return true; });
        when(logs.insert(any(ScheduledJobLogEntity.class))).thenAnswer(call -> { call.getArgument(0, ScheduledJobLogEntity.class).setId(99L); return 1; });
        return new ScheduledJobExecutionService(service, logs, registry, locks);
    }

    @Test void 同一计划时点重复触发只执行一次() {
        var execution = execution();
        var count = new AtomicInteger();
        when(logs.insert(any(ScheduledJobLogEntity.class))).thenAnswer(call -> {
            if (count.incrementAndGet() > 1) throw new DuplicateKeyException("duplicate trigger");
            call.getArgument(0, ScheduledJobLogEntity.class).setId(99L); return 1;
        });
        Instant scheduled = Instant.parse("2026-10-25T15:30:00Z");
        execution.execute(row, scheduled, null);
        execution.execute(row, scheduled, null);
        verify(leetcode, times(1)).check();
        verify(logs, times(2)).insert(argThat((ScheduledJobLogEntity e) -> e.getTriggerKey().equals("AUTO:" + scheduled.toEpochMilli())));
    }

    @Test void 暂停和旧配置回调不执行但暂停任务仍可手动执行() {
        var execution = execution();
        var old = new ScheduledJobEntity(); old.setId(row.getId()); old.setRevision(0);
        row.setRevision(1);
        execution.execute(old, Instant.now(), null);
        row.setEnabled(false);
        execution.execute(row, Instant.now(), null);
        verifyNoInteractions(leetcode);
        execution.execute(row, null, 1L);
        verify(leetcode).check();
    }

    @Test void 互斥失败只记跳过且业务异常记失败不泄露错误内容() {
        var execution = execution();
        doReturn(false).when(locks).tryRun(anyString(), any());
        execution.execute(row, null, 1L);
        verifyNoInteractions(leetcode);
        verify(logs).insert(argThat((ScheduledJobLogEntity e) -> "SKIPPED".equals(e.getStatus())));
        when(locks.tryRun(anyString(), any())).thenAnswer(call -> { call.getArgument(1, Runnable.class).run(); return true; });
        doThrow(new IllegalStateException("external-secret-response")).when(leetcode).check();
        execution.execute(row, null, 1L);
        verify(logs, atLeastOnce()).update(isNull(), argThat((Wrapper<ScheduledJobLogEntity> w) ->
                w instanceof com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update
                && update.getParamNameValuePairs().containsValue("FAILED")
                && update.getParamNameValuePairs().values().stream().noneMatch(v -> v.toString().contains("external-secret-response"))));
    }

    @Test void 调度刷新复用未修改配置并在暂停后移除旧触发器() {
        var execution = mock(ScheduledJobExecutionService.class);
        var runtime = new ScheduledJobRuntime(jobs, service, execution, registry);
        ReflectionTestUtils.setField(runtime, "enabled", true);
        when(jobs.selectCount(any())).thenReturn(1L);
        when(jobs.selectList(any())).thenReturn(List.of(row));
        runtime.initialize();
        try {
            runtime.start();
            var registrations = (Map<?, ?>) ReflectionTestUtils.getField(runtime, "registrations");
            assertEquals(1, registrations.size());
            Object first = registrations.get(row.getId());
            runtime.refresh(); assertSame(first, registrations.get(row.getId()));
            row.setRevision(1); row.setCron("0 0 7 * * ?");
            runtime.refresh(); assertNotSame(first, registrations.get(row.getId()));
            row.setEnabled(false); runtime.refresh(); assertTrue(registrations.isEmpty());
        } finally { runtime.close(); }
    }

    @Test void 真实Cron触发器将计划时间交给统一执行入口() {
        var execution = mock(ScheduledJobExecutionService.class);
        var runtime = new ScheduledJobRuntime(jobs, service, execution, registry);
        ReflectionTestUtils.setField(runtime, "enabled", true);
        var next = ZonedDateTime.now(ScheduledJobRules.ZONE).plusSeconds(3).withNano(0);
        row.setCron(next.getSecond() + " " + next.getMinute() + " " + next.getHour() + " "
                + next.getDayOfMonth() + " " + next.getMonthValue() + " ?");
        when(jobs.selectCount(any())).thenReturn(1L);
        when(jobs.selectList(any())).thenReturn(List.of(row));
        runtime.initialize();
        try {
            runtime.start();
            verify(execution, timeout(6000)).execute(eq(row), eq(next.toInstant()), isNull());
        } finally { runtime.close(); }
    }

    @Test void 异步基础设施失败会写入可见执行日志() {
        var execution = mock(ScheduledJobExecutionService.class);
        var runtime = new ScheduledJobRuntime(jobs, service, execution, registry);
        ReflectionTestUtils.setField(runtime, "enabled", true);
        when(jobs.selectCount(any())).thenReturn(1L);
        when(jobs.selectList(any())).thenReturn(List.of(row));
        doThrow(new IllegalStateException("Redis unavailable")).when(execution).execute(row, null, 1L);
        runtime.initialize();
        try {
            runtime.start(); runtime.runNow(row.getId(), 1L);
            verify(execution, timeout(2000)).infrastructureFailed(row, null, 1L);
        } finally { runtime.close(); }
    }

    @Test void 总开关关闭时拒绝手动执行且不注册自动任务() {
        var runtime = new ScheduledJobRuntime(jobs, service, mock(ScheduledJobExecutionService.class), registry);
        when(jobs.selectCount(any())).thenReturn(1L);
        runtime.initialize();
        try {
            runtime.start();
            assertThrows(IllegalArgumentException.class, () -> runtime.runNow(row.getId(), 1L));
            assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(runtime, "registrations")).isEmpty());
        } finally { runtime.close(); }
    }
}
