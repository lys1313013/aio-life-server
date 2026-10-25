package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.mapper.ExportCoordinatorMapper;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.service.*;
import top.aiolife.sso.mapper.UserMapper;

class ExportJobServiceTest {
    ExportJobMapper mapper = mock(ExportJobMapper.class);
    ExportCoordinatorMapper coordinator = mock(ExportCoordinatorMapper.class);
    ExportAuditRecorder audit = mock(ExportAuditRecorder.class);
    ExportJobService service;

    @BeforeEach
    void setup() {
        when(coordinator.lock()).thenReturn(1L);
        service =
                new ExportJobService(
                        mapper,
                        coordinator,
                        mock(UserMapper.class),
                        mock(ExportDatasetRegistry.class),
                        mock(ExportScope.class),
                        mock(ExportAccessGuard.class),
                        mock(ExportQuotaService.class),
                        mock(ExportAuthorizationService.class),
                        audit,
                        new ExportProperties());
    }

    @Test
    void 月额度最多五次且当天可连续申请没有日限制() {
        ExportQuotaService quota = new ExportQuotaService(new ExportProperties(), mapper);
        when(mapper.monthlyUsed(anyLong(), anyString())).thenReturn(4);
        assertEquals(1, quota.current(1).remaining());
        assertDoesNotThrow(() -> quota.requireRemaining(1));
        when(mapper.monthlyUsed(anyLong(), anyString())).thenReturn(5);
        assertThrows(ExportException.class, () -> quota.requireRemaining(1));
    }

    @Test
    void 跨用户取消不修改任务() {
        var job = job();
        when(mapper.lock(9)).thenReturn(job);
        assertThrows(ExportException.class, () -> service.cancel(2, 9));
        verify(mapper, never()).updateById(any(ExportJobEntity.class));
    }

    @Test
    void 已开始任务取消保留额度而系统失败退还原月额度() {
        var job = job();
        when(mapper.lock(9)).thenReturn(job);
        job.setCancelRequested(1);
        service.fail(job, "IO_FAILURE");
        assertEquals(1, job.getQuotaCharged());
        job = job();
        when(mapper.lock(9)).thenReturn(job);
        service.fail(job, "IO_FAILURE");
        assertEquals(0, job.getQuotaCharged());
        assertEquals("2026-09", job.getQuotaMonth());
    }

    @Test
    void 旧租约失败不能覆盖接管后任务也不能重复退款() {
        var old = job();
        old.setLeaseToken("old");
        var current = job();
        when(mapper.lock(9)).thenReturn(current);
        service.fail(old, "IO_FAILURE");
        verify(mapper, never()).updateById(any(ExportJobEntity.class));
        verifyNoInteractions(audit);
    }

    private ExportJobEntity job() {
        var job = new ExportJobEntity();
        job.setId(9L);
        job.setUserId(1L);
        job.setStatus(ExportStatus.RUNNING);
        job.setLeaseToken("new");
        job.setCancelRequested(0);
        job.setQuotaCharged(1);
        job.setQuotaMonth("2026-09");
        return job;
    }
}
