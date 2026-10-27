package top.aiolife.sso.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.export.storage.ExportArtifactStore;
import top.aiolife.relationship.repository.PersonRepository;
import top.aiolife.sso.mapper.AccountErasureMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountErasureServiceTest {
    AccountErasureMapper mapper = mock(AccountErasureMapper.class);
    MinioUtil minio = mock(MinioUtil.class);
    MinioConfig config = mock(MinioConfig.class);
    StorageObjectLock locks = mock(StorageObjectLock.class);
    ExportArtifactStore exports = mock(ExportArtifactStore.class);
    @SuppressWarnings("unchecked") ObjectProvider<PersonRepository> people = mock(ObjectProvider.class);
    CacheManager caches = mock(CacheManager.class);
    AccountErasureService service = new AccountErasureService(mapper, minio, config, locks, exports, people, caches);
    @BeforeEach void setup() {
        var user = new UserEntity(); user.setId(7L); user.setEmail("fixture@example.test");
        when(mapper.lockPending(7L)).thenReturn(user);
        when(config.getBucketName()).thenReturn("fixture-bucket");
        when(mapper.finish(7L)).thenReturn(1);
    }
    @Test void 文件删除失败保留数据库索引以便重试() throws Exception {
        when(mapper.privateObjects(7L)).thenReturn(List.of("fixture-avatar.png"));
        doThrow(new IllegalStateException("fixture storage failure")).when(minio).removeObject("fixture-bucket", "fixture-avatar.png");
        assertThrows(IllegalStateException.class, () -> service.erase(7));
        verify(mapper, never()).purgePersonalRecords(anyLong(), any()); verify(mapper, never()).finish(anyLong());
    }
    @Test void 共享引用出现后不会删除对象且锁覆盖整个事务() throws Exception {
        var object = new AccountErasureMapper.StoredObject(99L, "fixture-bucket", "fixture-shared.png");
        when(mapper.storedObjects(7L)).thenReturn(List.of(object));
        when(mapper.otherReferences(7L, 99L, "fixture-shared.png")).thenReturn(1L);
        service.erase(7);
        verify(locks).holdUntilTransactionCompletion("fixture-bucket", "fixture-shared.png");
        verifyNoInteractions(minio); verify(mapper).purgePersonalRecords(7L, "fixture@example.test"); verify(mapper).finish(7L);
    }
    @Test void 导出任务未停止时不能删除文件或清理数据库() {
        when(mapper.runningExports(eq(7L), any())).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.erase(7));
        verifyNoInteractions(minio, exports, people); verify(mapper, never()).purgePersonalRecords(anyLong(), any());
    }
    @Test void 清理失败持久化重试且不影响其他待清理账号() throws Exception {
        var workerService = mock(AccountErasureService.class);
        when(mapper.pending(any())).thenReturn(List.of(7L, 8L));
        doThrow(new IllegalStateException("fixture failure")).when(workerService).erase(7L);
        new AccountErasureWorker(mapper, workerService).cleanup();
        verify(mapper).retry(eq(7L), any()); verify(workerService).erase(8L); verify(mapper, never()).retry(eq(8L), any());
    }
}
