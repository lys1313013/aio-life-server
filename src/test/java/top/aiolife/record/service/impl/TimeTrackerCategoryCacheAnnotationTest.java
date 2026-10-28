package top.aiolife.record.service.impl;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.config.RedisConfig;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.TimeTrackerCategoryCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

/** 不依赖外部 Redis，使用真实 Spring 缓存代理、事务事件和事务完成回调。 */
class TimeTrackerCategoryCacheAnnotationTest {
    private AnnotationConfigApplicationContext context;
    private TimeTrackerCategoryCache categories;
    private CacheManager redisCaches;
    private TransactionTemplate transaction;

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config implements CachingConfigurer {
        @Bean("cacheManager")
        CacheManager redisCaches() {
            return spy(new ConcurrentMapCacheManager(TimeTrackerCategoryCache.CACHE_NAME));
        }

        @Bean
        @Primary
        CacheManager caffeineCacheManager() {
            return new ConcurrentMapCacheManager(TimeTrackerCategoryCache.CACHE_NAME);
        }

        @Bean
        TimeTrackerCategoryCache categories(ApplicationEventPublisher events) {
            return new TimeTrackerCategoryCache(events);
        }

        @Bean
        TestTransactionManager transactionManager() { return new TestTransactionManager(); }

        @Override
        public CacheErrorHandler errorHandler() { return new RedisConfig().errorHandler(); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        categories = context.getBean(TimeTrackerCategoryCache.class);
        redisCaches = context.getBean("cacheManager", CacheManager.class);
        transaction = new TransactionTemplate(context.getBean(TestTransactionManager.class));
    }

    @AfterEach
    void cleanup() {
        context.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void testCache_重复和空列表命中指定Redis管理器且用户隔离() {
        var loads = new AtomicInteger();
        categories.list(1L, () -> { loads.incrementAndGet(); return List.of(row(11L)); });
        assertEquals(11L, categories.list(1L, () -> fail("重复查询应命中缓存")).getFirst().getId());
        assertEquals(22L, categories.list(2L, () -> List.of(row(22L))).getFirst().getId());
        categories.list(3L, () -> { loads.incrementAndGet(); return List.of(); });
        assertTrue(categories.list(3L, () -> fail("空列表也应缓存")).isEmpty());
        assertEquals(2, loads.get());
        assertNotNull(cache().get(1L));
        assertNotNull(cache().get(2L));
        assertNull(context.getBean("caffeineCacheManager", CacheManager.class)
                .getCache(TimeTrackerCategoryCache.CACHE_NAME).get(1L));
    }

    @Test
    void testTransaction_事务读取不命中也不污染共享缓存() {
        categories.list(1L, () -> List.of(row(11L)));
        transaction.executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(12L, categories.list(1L, () -> List.of(row(12L))).getFirst().getId());
            categories.list(2L, () -> List.of(row(22L)));
            assertNull(cache().get(2L));
        });
        assertEquals(11L, categories.list(1L, () -> fail("事务读取不能覆盖已提交缓存")).getFirst().getId());
        assertEquals(23L, categories.list(2L, () -> List.of(row(23L))).getFirst().getId());
    }

    @Test
    void testCommit_提交后仅清理受影响所有者() {
        warm(0L, 1L, 2L);
        transaction.executeWithoutResult(status -> {
            categories.evictAfterCommit(1L);
            assertNotNull(cache().get(1L));
        });
        assertNull(cache().get(1L));
        assertNotNull(cache().get(0L));
        assertNotNull(cache().get(2L));

        transaction.executeWithoutResult(status -> categories.evictAfterCommit(0L));
        assertNull(cache().get(0L));
        assertNotNull(cache().get(2L));
    }

    @Test
    void testRollback_回滚保留已提交分类缓存() {
        warm(1L);
        transaction.executeWithoutResult(status -> {
            categories.evictAfterCommit(1L);
            status.setRollbackOnly();
        });
        assertNotNull(cache().get(1L));
        assertEquals(1L, categories.list(1L, () -> fail("回滚不应清除缓存")).getFirst().getId());
    }

    @Test
    void testFallback_无事务和SUPPORTS空同步上下文均立即清理() {
        warm(1L, 2L);
        categories.evictAfterCommit(1L);
        assertNull(cache().get(1L));
        var supports = new TransactionTemplate(context.getBean(TestTransactionManager.class));
        supports.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        supports.executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            categories.evictAfterCommit(2L);
            assertNull(cache().get(2L));
        });
    }

    private Cache cache() { return redisCaches.getCache(TimeTrackerCategoryCache.CACHE_NAME); }

    private void warm(Long... owners) {
        for (Long owner : owners) categories.list(owner, () -> List.of(row(owner)));
    }

    private TimeTrackerCategoryEntity row(Long id) {
        var row = new TimeTrackerCategoryEntity();
        row.setId(id);
        row.setName("分类" + id);
        return row;
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() { return new Object(); }
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override
        protected void doCommit(DefaultTransactionStatus status) { }
        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
