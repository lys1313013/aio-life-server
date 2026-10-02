package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.service.impl.MenuServiceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuDataCacheTest {
    private final UserSecondaryLockMenuMapper locks = mock(UserSecondaryLockMenuMapper.class);
    private final ISysMenuMapper menus = mock(ISysMenuMapper.class);
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, String> storage = new HashMap<>();
    private final Map<String, Long> expiration = new HashMap<>();
    private long clock = 0;
    private MenuDataCache cache;
    private RedisUtil redis;

    @BeforeEach
    void setup() {
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            return expiration.getOrDefault(key, Long.MAX_VALUE) > clock ? storage.get(key) : null;
        });
        doAnswer(call -> { storage.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString());
        doAnswer(call -> {
            String key = call.getArgument(0);
            storage.put(key, call.getArgument(1));
            expiration.put(key, clock + ((TimeUnit) call.getArgument(3)).toMillis(call.getArgument(2)));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        redis = new RedisUtil(template, mapper);
        cache = new MenuDataCache(locks, menus, redis);
    }

    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test
    void lockedIds_按用户隔离并缓存空结果五小时() {
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(lock(9223372036854775807L)), List.of(lock(8L)));
        assertEquals(List.of(), cache.getLockedMenuIds(11));
        assertEquals(List.of(), cache.getLockedMenuIds(11));
        assertEquals(List.of(9223372036854775807L), cache.getLockedMenuIds(22));
        assertEquals(List.of(9223372036854775807L), cache.getLockedMenuIds(22));
        clock = TimeUnit.HOURS.toMillis(5) - 1;
        assertEquals(List.of(), cache.getLockedMenuIds(11));
        verify(locks, times(2)).selectList(any(Wrapper.class));
        clock++;
        assertEquals(List.of(8L), cache.getLockedMenuIds(11));
        verify(values, times(3)).set(anyString(), anyString(), eq(5L), eq(TimeUnit.HOURS));
    }

    @Test
    void sharedCache_不同实例复用且失效后读取新配置() {
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(lock(1L)), List.of(lock(2L)));
        var other = new MenuDataCache(locks, menus, redis);
        assertEquals(List.of(1L), cache.getLockedMenuIds(11));
        assertEquals(List.of(1L), other.getLockedMenuIds(11));
        verify(locks).selectList(any(Wrapper.class));
        cache.evictLockedMenuIds(11);
        assertEquals(List.of(2L), other.getLockedMenuIds(11));
    }

    @Test
    void invalidate_迟到的查询只能回填旧版本() {
        when(locks.selectList(any(Wrapper.class))).thenAnswer(call -> {
            cache.evictLockedMenuIds(11);
            return List.of(lock(1L));
        }).thenReturn(List.of(lock(2L)));
        assertEquals(List.of(1L), cache.getLockedMenuIds(11));
        assertEquals(List.of(2L), cache.getLockedMenuIds(11));
        assertEquals(List.of(2L), cache.getLockedMenuIds(11));
        verify(locks, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void transaction_提交后失效且事务内不回填共享缓存() {
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(lock(1L)), List.of(lock(2L)), List.of(lock(3L)));
        assertEquals(List.of(1L), cache.getLockedMenuIds(11));
        beginTransaction();
        cache.evictLockedMenuIds(11);
        verify(values, never()).set(anyString(), anyString());
        assertEquals(List.of(2L), cache.getLockedMenuIds(11));
        verify(values).set(anyString(), anyString(), eq(5L), eq(TimeUnit.HOURS));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clear();
        assertEquals(List.of(3L), cache.getLockedMenuIds(11));
    }

    @Test
    void transaction_回滚不失效已提交缓存() {
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(lock(1L)));
        cache.getLockedMenuIds(11);
        beginTransaction();
        cache.evictLockedMenuIds(11);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clear();
        assertEquals(List.of(1L), cache.getLockedMenuIds(11));
        verify(locks).selectList(any(Wrapper.class));
    }

    @Test
    void enabledMenus_共享数据仍按角色过滤且菜单修改立即改变锁继承() {
        var parent = menu(1L, 0L, "/record", null);
        var child = menu(2L, 1L, "/my-hub/honor", "admin");
        when(menus.selectList(any(Wrapper.class))).thenReturn(List.of(parent, child));
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(lock(1L)));
        var service = new MenuServiceImpl(menus, mapper, cache);
        assertEquals(Set.of(1L), service.getAccessibleMenuIds(List.of("user")));
        assertEquals(Set.of(1L, 2L), service.getAccessibleMenuIds(List.of("admin")));
        var guardCache = new SecondaryLockMenuCache(cache);
        assertEquals(Set.of("/record"), guardCache.findMatchedPaths(11, "/honorRecords/7"));
        verify(menus).selectList(any(Wrapper.class));
        var moved = menu(2L, 0L, "/my-hub/honor", "admin");
        when(menus.selectList(any(Wrapper.class))).thenReturn(List.of(parent, moved));
        cache.evictMenus();
        assertEquals(Set.of(), guardCache.findMatchedPaths(11, "/honorRecords/7"));
        verify(menus, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void menuWrites_增删改启停排序全部失效且失败不失效() throws Exception {
        var dataCache = mock(MenuDataCache.class);
        var service = new MenuServiceImpl(menus, mapper, dataCache);
        var row = menu(2L, 0L, "/my-hub/memo", null);
        when(menus.selectById(2L)).thenReturn(row);
        when(menus.selectCount(any(Wrapper.class))).thenReturn(0L);
        var req = new MenuSaveReq(); req.setName("memo"); req.setPath("/my-hub/memo");
        service.create(req, 11);
        service.update(2L, req, 11);
        service.updateStatus(2L, 0, 11);
        service.updateSort(2L, 5, 11);
        service.delete(2L, 11);
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(2L, 7, 11));
        verify(dataCache, times(5)).evictMenus();
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static UserSecondaryLockMenuEntity lock(long id) {
        var row = new UserSecondaryLockMenuEntity(); row.setMenuId(id); return row;
    }

    private static SysMenuEntity menu(long id, long parent, String path, String roles) {
        var row = new SysMenuEntity(); row.setId(id); row.setParentId(parent); row.setPath(path);
        row.setRoles(roles); row.setIsDeleted(0); row.setSort(0); row.setStatus(1); return row;
    }
}
