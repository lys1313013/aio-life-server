package top.aiolife.database;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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

/** 真实MySQL验证乐观更新、唯一触发键、执行日志和分页；业务调用模拟，不发通知。 */
@ActiveProfiles("test")
@SpringBootTest(classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ScheduledJobPersistenceTest {
    @Autowired ScheduledJobMapper jobs;
    @Autowired ScheduledJobLogMapper logs;

    @Test void 配置保存日志分页和重复触发在真实MySQL上闭环() {
        var leetcode = mock(ILeetcodeService.class);
        var registry = new ScheduledJobRegistry(leetcode);
        var service = new ScheduledJobService(jobs, logs, registry, mock(ApplicationEventPublisher.class), new MockEnvironment());
        service.initializeDefaults(); service.initializeDefaults();
        assertEquals(2, service.list().size());
        var row = jobs.selectOne(new LambdaQueryWrapper<ScheduledJobEntity>().eq(ScheduledJobEntity::getJobCode, ScheduledJobRegistry.CHECK));
        var req = new ScheduledJobUpdateReq(); req.setCron("0 0 22 * * ?"); req.setIsEnabled(0); req.setRevision(0);
        var saved = service.update(row.getId(), req, 1L);
        assertEquals(0, saved.getIsEnabled()); assertEquals(1, saved.getRevision());
        final Long jobId = row.getId();
        assertThrows(IllegalArgumentException.class, () -> service.update(jobId, req, 1L));
        service.initializeDefaults(); assertEquals(0, service.require(row.getId()).getIsEnabled());
        req.setRevision(1); req.setIsEnabled(1); service.update(row.getId(), req, 1L);
        row = service.require(row.getId());
        var locks = mock(DistributedLockExecutor.class);
        when(locks.tryRun(anyString(), any())).thenAnswer(call -> { call.getArgument(1, Runnable.class).run(); return true; });
        var execution = new ScheduledJobExecutionService(service, logs, registry, locks);
        Instant scheduled = Instant.parse("2026-10-25T14:00:00Z");
        execution.execute(row, scheduled, null); execution.execute(row, scheduled, null);
        verify(leetcode, times(1)).check();
        var first = service.logs(row.getId(), 1, 1);
        assertEquals(1L, first.getTotal()); assertEquals("SUCCESS", first.getItems().getFirst().getStatus());
        assertNotNull(first.getItems().getFirst().getDurationMs());
        doThrow(new IllegalStateException("private response")).when(leetcode).check();
        execution.execute(row, null, 1L);
        var second = service.logs(row.getId(), 1, 1);
        assertEquals(2L, second.getTotal()); assertEquals("FAILED", second.getItems().getFirst().getStatus());
        assertEquals(1L, second.getItems().getFirst().getCreateUser());
        assertFalse(second.getItems().getFirst().getMessage().contains("private response"));
        assertEquals("SUCCESS", service.logs(row.getId(), 2, 1).getItems().getFirst().getStatus());
        var orphan = new ScheduledJobLogEntity(); orphan.fillCreateCommonField(null);
        orphan.setJobId(row.getId()); orphan.setTriggerKey("orphan"); orphan.setTriggerType("AUTO");
        orphan.setStatus("RUNNING"); orphan.setStartTime(LocalDateTime.now()); orphan.setInstanceId("old-instance");
        logs.insert(orphan); execution.recover(row.getId());
        assertEquals("INTERRUPTED", logs.selectById(orphan.getId()).getStatus());
        assertNull(logs.selectById(orphan.getId()).getDurationMs());
        final Long id = row.getId();
        assertThrows(IllegalArgumentException.class, () -> service.logs(id, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.logs(id, 1, 201));
    }
}
