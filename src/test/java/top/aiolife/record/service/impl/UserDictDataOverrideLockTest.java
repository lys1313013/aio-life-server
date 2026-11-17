package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.aiolife.support.MockMatchers.anyWrapper;

/** 公共字典覆盖写入由 Redisson 锁串行化，锁忙时拒绝写入而不是依赖唯一索引兜底。 */
class UserDictDataOverrideLockTest {

    private static final String LOCK_KEY = "record:user-dict:override:1:7";

    private final UserDictDataCache cache = mock(UserDictDataCache.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final UserDictDataServiceImpl service = spy(new UserDictDataServiceImpl(cache, locks));

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "dict"), UserDictDataEntity.class);
        when(locks.tryRun(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
    }

    private UserDictDataEntity row(Long userId) {
        var row = new UserDictDataEntity();
        row.setId(7L);
        row.setUserId(userId);
        row.setDictType("exercise_type");
        return row;
    }

    @Test
    void testUpdate_公共项按用户与预设加锁并写入覆盖() {
        doReturn(row(null)).when(service).getById(7L);
        doReturn(null).when(service).getOne(anyWrapper());
        doReturn(true).when(service).save(any(UserDictDataEntity.class));

        service.updateDictData(7L, new UserDictDataEntity(), 1L);

        verify(locks).tryRun(eq(LOCK_KEY), any(Runnable.class));
        var saved = ArgumentCaptor.forClass(UserDictDataEntity.class);
        verify(service).save(saved.capture());
        assertEquals(Long.valueOf(1L), saved.getValue().getUserId());
        assertEquals(Long.valueOf(7L), saved.getValue().getTemplateId());
    }

    @Test
    void testUpdate_锁被占用时拒绝公共项写入() {
        doReturn(row(null)).when(service).getById(7L);
        when(locks.tryRun(anyString(), any(Runnable.class))).thenReturn(false);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateDictData(7L, new UserDictDataEntity(), 1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(service, never()).save(any(UserDictDataEntity.class));
        verify(service, never()).updateById(any(UserDictDataEntity.class));
        verifyNoInteractions(cache);
    }

    @Test
    void testUpdate_私有字典不进入覆盖锁() {
        doReturn(row(1L)).when(service).getById(7L);
        doReturn(true).when(service).update(any(UserDictDataEntity.class), anyWrapper());

        service.updateDictData(7L, new UserDictDataEntity(), 1L);

        verify(locks, never()).tryRun(anyString(), any(Runnable.class));
    }

    @Test
    void testDelete_公共项在锁内首次隐藏() {
        doReturn(row(null)).when(service).getById(7L);
        doReturn(null).when(service).getOne(anyWrapper());
        doReturn(true).when(service).save(any(UserDictDataEntity.class));

        service.deleteDictData(7L, 1L);

        verify(locks).tryRun(eq(LOCK_KEY), any(Runnable.class));
        var saved = ArgumentCaptor.forClass(UserDictDataEntity.class);
        verify(service).save(saved.capture());
        assertEquals(0, saved.getValue().getIsEnabled().intValue());
        assertEquals(Long.valueOf(7L), saved.getValue().getTemplateId());
    }
}