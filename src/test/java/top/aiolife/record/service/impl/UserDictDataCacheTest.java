package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.JsonConfig;
import top.aiolife.config.RedisConfig;
import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDictDataCacheTest {
    private static final String TYPE = "exercise_type";
    private AnnotationConfigApplicationContext context;
    private UserDictDataMapper mapper;
    private UserDictDataCache dictionaries;
    private UserDictDataServiceImpl service;
    private CacheManager caches;

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class Config implements CachingConfigurer {
        @Bean
        public CacheManager cacheManager() { return spy(new ConcurrentMapCacheManager(UserDictDataCache.CACHE_NAME)); }
        @Bean
        @Primary
        CacheManager caffeineCacheManager() { return new ConcurrentMapCacheManager(); }
        @Override
        public CacheErrorHandler errorHandler() { return new RedisConfig().errorHandler(); }
        @Bean
        UserDictDataMapper mapper() { return mock(UserDictDataMapper.class); }
        @Bean
        UserDictDataCache dictionaries() { return new UserDictDataCache(mapper(), cacheManager()); }
    }

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "dict"), UserDictDataEntity.class);
        context = new AnnotationConfigApplicationContext(Config.class);
        mapper = context.getBean(UserDictDataMapper.class);
        dictionaries = context.getBean(UserDictDataCache.class);
        caches = context.getBean("cacheManager", CacheManager.class);
        service = spy(new UserDictDataServiceImpl(dictionaries));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
        context.close();
    }

    @Test
    void testRead_重复读取命中Redis管理器且用户类型隔离() {
        service.listUserVisibleDictData(1L, TYPE);
        service.listUserVisibleDictData(1L, TYPE);
        verify(mapper, times(2)).selectList(any(Wrapper.class));
        service.listUserVisibleDictData(2L, TYPE);
        verify(mapper, times(3)).selectList(any(Wrapper.class)); // 公共部分复用
        service.listUserVisibleDictData(1L, "income_type");
        verify(mapper, times(5)).selectList(any(Wrapper.class));
        assertCached(0L, TYPE, true);
        assertCached(1L, TYPE, true);
        assertNull(context.getBean("caffeineCacheManager", CacheManager.class)
                .getCache(UserDictDataCache.CACHE_NAME).get("1:" + TYPE));
    }

    @Test
    void testVisibility_缓存停用覆盖记录但业务列表仍隐藏且管理页可见() {
        var base = row(7L, 0L, TYPE);
        var override = row(8L, 1L, TYPE);
        override.setTemplateId(7L);
        override.setStatus("1");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(base), List.of(override));
        assertTrue(service.listUserVisibleDictData(1L, TYPE).isEmpty());
        assertEquals("1", service.listUserVisibleDictData(1L, TYPE, true).getFirst().getStatus());
        assertTrue(service.listUserVisibleDictData(1L, TYPE).isEmpty());
        verify(mapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void testRead_事务内绕过缓存且银行卡标签不缓存() {
        dictionaries.list(1L, TYPE);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        dictionaries.list(1L, TYPE);
        dictionaries.list(1L, TYPE);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        dictionaries.list(1L, "bank_card_tag");
        dictionaries.list(1L, "bank_card_tag");
        verify(mapper, times(5)).selectList(any(Wrapper.class));
        assertCached(1L, "bank_card_tag", false);
    }

    @Test
    void testCreate_只清理本人对应类型且空列表会失效() {
        warm();
        doReturn(true).when(service).save(any(UserDictDataEntity.class));
        service.createDictData(row(null, 1L, TYPE), 1L);
        assertCached(1L, TYPE, false);
        assertCached(0L, TYPE, true);
        assertCached(2L, TYPE, true);
        assertCached(1L, "income_type", true);
    }

    @Test
    void testUpdate_提交后清理旧类型新类型并保留其他用户() {
        warm();
        doReturn(row(7L, 1L, TYPE)).when(service).getById(7L);
        doReturn(true).when(service).update(any(UserDictDataEntity.class), any(Wrapper.class));
        TransactionSynchronizationManager.initSynchronization();
        service.updateDictData(7L, row(7L, 1L, "income_type"), 1L);
        assertCached(1L, TYPE, true);
        assertCached(1L, "income_type", true);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertCached(1L, TYPE, false);
        assertCached(1L, "income_type", false);
        assertCached(2L, TYPE, true);
    }

    @Test
    void testRollback_不清除已提交数据的缓存() {
        warm();
        doReturn(true).when(service).save(any(UserDictDataEntity.class));
        TransactionSynchronizationManager.initSynchronization();
        service.createDictData(row(null, 1L, TYPE), 1L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertCached(1L, TYPE, true);
    }

    @Test
    void testDelete_删除私有分类清理本人缓存() {
        warm();
        doReturn(row(7L, 1L, TYPE)).when(service).getById(7L);
        doReturn(true).when(service).removeById(7L);
        service.deleteDictData(7L, 1L);
        assertCached(1L, TYPE, false);
        assertCached(0L, TYPE, true);
        assertCached(2L, TYPE, true);
    }

    @Test
    void testHide_隐藏公共分类只清本人缓存() {
        warm();
        doReturn(row(7L, 0L, TYPE)).when(service).getById(7L);
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(UserDictDataEntity.class));
        service.deleteDictData(7L, 1L);
        assertCached(1L, TYPE, false);
        assertCached(0L, TYPE, true);
        assertCached(2L, TYPE, true);
    }

    @Test
    void testOverride_修改只读公共分类状态清理本人缓存() {
        warm();
        var base = row(7L, 0L, TYPE);
        base.setIsReadonly("Y");
        doReturn(base).when(service).getById(7L);
        doReturn(row(8L, 1L, TYPE)).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).updateById(any(UserDictDataEntity.class));
        var updates = row(7L, 1L, TYPE);
        updates.setStatus("1");
        service.updateDictData(7L, updates, 1L);
        assertCached(1L, TYPE, false);
        assertCached(0L, TYPE, true);
    }

    @Test
    void testAdmin_新增修改删除清除共享缓存且所有用户读取新值() {
        warm();
        doReturn(true).when(service).save(any(UserDictDataEntity.class));
        assertTrue(service.createBaseDictData(row(null, 0L, TYPE)));
        assertCached(0L, TYPE, false);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(7L, 0L, TYPE)));
        assertEquals(7L, service.listUserVisibleDictData(1L, TYPE).getFirst().getId());
        assertEquals(7L, service.listUserVisibleDictData(2L, TYPE).getFirst().getId());
        doReturn(row(7L, 0L, TYPE)).when(service).getById(7L);
        doReturn(true).when(service).updateById(any(UserDictDataEntity.class));
        cache().put("0:income_type", List.of());
        assertTrue(service.updateBaseDictData(7L, row(7L, 0L, "income_type")));
        assertCached(0L, TYPE, false);
        assertCached(0L, "income_type", false);
        cache().put("0:" + TYPE, List.of());
        doReturn(true).when(service).removeById(7L);
        assertTrue(service.deleteBaseDictData(7L));
        assertCached(0L, TYPE, false);
        assertCached(1L, TYPE, true);
        assertCached(2L, TYPE, true);
    }

    @Test
    void testAdminFailure_越权与写入失败不清缓存() {
        warm();
        doReturn(row(7L, 2L, TYPE)).when(service).getById(7L);
        assertThrows(RuntimeException.class, () -> service.updateBaseDictData(7L, row(7L, 0L, TYPE)));
        assertThrows(RuntimeException.class, () -> service.deleteBaseDictData(7L));
        doReturn(false).when(service).save(any(UserDictDataEntity.class));
        assertFalse(service.createBaseDictData(row(null, 0L, TYPE)));
        assertCached(0L, TYPE, true);
    }

    @Test
    void testSort_公共排序在提交后清缓存() {
        warm();
        var first = row(7L, 0L, TYPE);
        var second = row(8L, 0L, TYPE);
        second.setDictSort(10);
        doReturn(new ArrayList<>(List.of(first, second))).when(service).list(any(Wrapper.class));
        doReturn(true).when(service).updateBatchById(anyCollection());
        TransactionSynchronizationManager.initSynchronization();
        var sorted = service.reSortBaseDictData(TYPE, 8L, 7L, "before");
        assertEquals(8L, sorted.getFirst().getId());
        assertCached(0L, TYPE, true);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertCached(0L, TYPE, false);
        assertCached(1L, TYPE, true);
    }

    @Test
    void testRedisFailure_读写故障回源且清除故障不影响已完成写入() {
        var failed = mock(Cache.class);
        when(failed.getName()).thenReturn(UserDictDataCache.CACHE_NAME);
        when(failed.get(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable")).when(failed).put(any(), any());
        doThrow(new IllegalStateException("Redis unavailable")).when(failed).evict(any());
        doReturn(failed).when(caches).getCache(UserDictDataCache.CACHE_NAME);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row(7L, 0L, TYPE)));
        assertEquals(7L, dictionaries.list(0L, TYPE).getFirst().getId());
        assertDoesNotThrow(() -> dictionaries.evictAfterCommit(0L, TYPE));
        verify(failed).evict("0:" + TYPE);
    }

    @Test
    void testRedisConfiguration_五分钟过期且大整数日期空列表可往返() {
        var json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        var manager = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class), json);
        manager.afterPropertiesSet();
        var config = manager.getCacheConfigurations().get(UserDictDataCache.CACHE_NAME);
        assertEquals(Duration.ofMinutes(5), config.getTtl());
        var item = row(9223372036854775806L, 1L, TYPE);
        item.setTemplateId(9223372036854775805L);
        item.setCreateTime(LocalDateTime.of(2026, 10, 4, 12, 30));
        for (var rows : List.of(List.of(item), List.<UserDictDataEntity>of())) {
            var encoded = config.getValueSerializationPair().getWriter().write(rows);
            var decoded = assertInstanceOf(List.class, config.getValueSerializationPair().getReader().read(encoded));
            assertEquals(json.valueToTree(rows), json.valueToTree(decoded));
            if (!decoded.isEmpty()) {
                var restored = assertInstanceOf(UserDictDataEntity.class, decoded.getFirst());
                assertEquals(item.getId(), restored.getId());
                assertEquals(item.getTemplateId(), restored.getTemplateId());
                assertEquals(item.getCreateTime(), restored.getCreateTime());
            }
        }
    }

    private Cache cache() { return caches.getCache(UserDictDataCache.CACHE_NAME); }
    private void warm() {
        for (long userId : List.of(0L, 1L, 2L)) dictionaries.list(userId, TYPE);
        dictionaries.list(1L, "income_type");
    }
    private void assertCached(long userId, String type, boolean expected) {
        assertEquals(expected, cache().get(userId + ":" + type) != null);
    }
    private UserDictDataEntity row(Long id, Long owner, String type) {
        var row = new UserDictDataEntity();
        row.setId(id); row.setUserId(owner); row.setDictType(type);
        row.setDictLabel("运动"); row.setDictValue("exercise"); row.setStatus("0"); row.setDictSort(0);
        return row;
    }
}
