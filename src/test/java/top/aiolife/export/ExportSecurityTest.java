package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletResponse;

import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.mapper.ExportSecurityMapper;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.service.ExportAccessGuard;
import top.aiolife.export.service.ExportAuditRecorder;
import top.aiolife.export.service.ExportAuthorizationService;
import top.aiolife.export.service.ExportDownloadService;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.service.ExportScope;
import top.aiolife.export.service.ExportTime;
import top.aiolife.export.storage.ExportArtifactStore;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.io.ByteArrayInputStream;
import java.util.List;

class ExportSecurityTest {
    private final ExportSecurityMapper security = mock(ExportSecurityMapper.class);
    private final RedisUtil redis = mock(RedisUtil.class);
    private final ExportAccessGuard guard = new ExportAccessGuard(security, redis);

    @Test
    void 所有菜单祖先必须在同一端启用且满足角色() {
        setupMenus("user", false);
        var dataset = dataset();
        assertDoesNotThrow(() -> guard.check(7L, List.of(dataset), true));
        var parent = security.menus().get(1);
        parent.setWebIsEnabled(0);
        parent.setMobileIsEnabled(0);
        assertThrows(ExportException.class, () -> guard.check(7L, List.of(dataset), true));
        parent.setWebIsEnabled(1);
        parent.setRoles("admin");
        assertThrows(ExportException.class, () -> guard.check(7L, List.of(dataset), true));
    }

    @Test
    void 管理员不能绕过个人中心或业务祖先锁() {
        setupMenus("admin", true);
        assertThrows(
                SecondaryLockRequiredException.class,
                () -> guard.check(7L, List.of(dataset()), true));
        when(redis.hasKey(anyString())).thenReturn(true);
        assertDoesNotThrow(() -> guard.check(7L, List.of(dataset()), true));
        when(security.lockedMenus(7L)).thenReturn(List.of(1L));
        when(redis.hasKey(anyString())).thenReturn(false);
        assertThrows(SecondaryLockRequiredException.class, () -> guard.check(7L, List.of(), true));
    }

    @Test
    void 下载拒绝其他用户与过期文件且不消费核验凭证() {
        var access = mock(ExportAccessGuard.class);
        var jobs = mock(ExportJobMapper.class);
        var auth = mock(ExportAuthorizationService.class);
        var service = downloadService(access, jobs, auth, mock(ExportAuditRecorder.class));
        var job = job();
        when(jobs.lock(3L)).thenReturn(job);
        assertThrows(ExportException.class, () -> service.begin(8L, 3L, "grant"));
        job.setExpiresAt(ExportTime.now().minusSeconds(1));
        assertThrows(ExportException.class, () -> service.begin(7L, 3L, "grant"));
        verifyNoInteractions(auth);
        verify(jobs, never()).updateById(any(ExportJobEntity.class));
    }

    @Test
    void 下载开始后权限撤销不输出文件字节且记录中断() throws Exception {
        var access = mock(ExportAccessGuard.class);
        var jobs = mock(ExportJobMapper.class);
        var audit = mock(ExportAuditRecorder.class);
        var store = mock(ExportArtifactStore.class);
        var service = downloadService(access, jobs, mock(ExportAuthorizationService.class), audit);
        var job = job();
        when(jobs.current(3L)).thenReturn(job);
        when(access.user(7L)).thenReturn(new UserEntity());
        when(store.open("private-object"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        doThrow(ExportException.denied()).when(access).check(eq(7L), anyList(), eq(true));
        var response = new MockHttpServletResponse();
        assertThrows(
                ExportException.class,
                () ->
                        service.stream(
                                new ExportDownloadService.Transfer(
                                        job,
                                        "transfer",
                                        ExportAccessGuard.securityHash(new UserEntity())),
                                store,
                                response));
        assertEquals(0, response.getContentAsByteArray().length);
        verify(audit)
                .independent(7L, 3L, "DOWNLOAD_INTERRUPTED", "INTERRUPTED", "transfer,bytes=0");
    }

    private void setupMenus(String role, boolean locked) {
        var user = new UserEntity();
        user.setId(7L);
        user.setRole(role);
        when(security.user(7L)).thenReturn(user);
        when(security.menus())
                .thenReturn(
                        List.of(
                                menu(1L, 0L, "/profile"),
                                menu(2L, 0L, "/finance"),
                                menu(3L, 2L, "/finance/expenses")));
        when(security.lockedMenus(7L)).thenReturn(locked ? List.of(2L) : List.of());
    }

    private SysMenuEntity menu(long id, long parent, String path) {
        var menu = new SysMenuEntity();
        menu.setId(id);
        menu.setParentId(parent);
        menu.setPath(path);
        menu.setWebIsEnabled(1);
        menu.setMobileIsEnabled(1);
        return menu;
    }

    private ExportDataset dataset() {
        var dataset = mock(ExportDataset.class);
        when(dataset.available()).thenReturn(true);
        when(dataset.menus()).thenReturn(List.of("/finance/expenses"));
        return dataset;
    }

    private ExportDownloadService downloadService(
            ExportAccessGuard access,
            ExportJobMapper jobs,
            ExportAuthorizationService auth,
            ExportAuditRecorder audit) {
        var scope = mock(ExportScope.class);
        when(scope.decode(anyString())).thenReturn(List.of());
        return new ExportDownloadService(
                mock(RedissonClient.class),
                jobs,
                access,
                scope,
                auth,
                audit,
                new ExportProperties());
    }

    private ExportJobEntity job() {
        var job = new ExportJobEntity();
        job.setId(3L);
        job.setUserId(7L);
        job.setStatus("SUCCEEDED");
        job.setArtifactStatus("AVAILABLE");
        job.setExpiresAt(ExportTime.now().plusHours(1));
        job.setScopeJson("[]");
        job.setObjectKey("private-object");
        job.setContentType("application/zip");
        job.setFileName("export.zip");
        job.setFileBytes(3L);
        return job;
    }
}
