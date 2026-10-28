package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
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
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.config.JsonConfig;
import top.aiolife.config.RedisConfig;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.TimeTrackerCategoryCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只连接显式指定的临时 Redis；独立 Spring 上下文验证缓存代理和事务事件。 */
@EnabledIfEnvironmentVariable(named = "AIO_CATEGORY_TEST_REDIS_PORT", matches = ".+")
class TimeTrackerCategoryCacheRedisTest {
    private static AnnotationConfigApplicationContext firstContext;
    private static AnnotationConfigApplicationContext secondContext;
    private static StringRedisTemplate redis;
    private static TimeTrackerCategoryCache first;
    private static TimeTrackerCategoryCache second;
    private final List<String> keys = new ArrayList<>();

    @Configuration
    @EnableCaching(proxyTargetClass = true)
    @EnableTransactionManagement
    static class Config implements CachingConfigurer {
        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory connectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1",
                    Integer.parseInt(System.getenv("AIO_CATEGORY_TEST_REDIS_PORT")));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        }

        @Bean
        StringRedisTemplate redis(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
            return spy(new RedisConfig().cacheManager(connectionFactory, objectMapper));
        }

        @Bean
        @Primary
        CacheManager caffeineCacheManager() { return new ConcurrentMapCacheManager(); }

        @Override
        public CacheErrorHandler errorHandler() { return new RedisConfig().errorHandler(); }

        @Bean
        TimeTrackerCategoryCache categories(ApplicationEventPublisher events) {
            return new TimeTrackerCategoryCache(events);
        }

        @Bean
        PlatformTransactionManager transactionManager() { return new TestTransactionManager(); }
    }

    /** 使用 Spring 的完整提交/回滚同步流程，不依赖业务数据库。 */
    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() { return new Object(); }
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override
        protected void doCommit(DefaultTransactionStatus status) {}
        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @BeforeAll
    static void setup() {
        firstContext = new AnnotationConfigApplicationContext(Config.class);
        secondContext = new AnnotationConfigApplicationContext(Config.class);
        redis = firstContext.getBean(StringRedisTemplate.class);
        first = firstContext.getBean(TimeTrackerCategoryCache.class);
        second = secondContext.getBean(TimeTrackerCategoryCache.class);
    }

    @AfterEach
    void cleanup() {
        if (!keys.isEmpty()) redis.delete(keys);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @AfterAll
    static void close() {
        if (firstContext != null) firstContext.close();
        if (secondContext != null) secondContext.close();
    }

    @Test
    void testRead_跨实例命中Redis且不使用默认本地缓存() {
        assertTrue(AopUtils.isAopProxy(first));
        assertTrue(AopUtils.isAopProxy(second));
        long owner = owner();
        long other = owner();
        AtomicInteger loads = new AtomicInteger();
        Supplier<List<TimeTrackerCategoryEntity>> loader = () -> {
            loads.incrementAndGet();
            return List.of(row(owner, "原值"));
        };
        assertEquals("原值", first.list(owner, loader).getFirst().getName());
        assertEquals("原值", second.list(owner, loader).getFirst().getName());
        second.list(other, loader);
        assertEquals(2, loads.get());
        assertTrue(redis.hasKey(key(owner)));
        var localCaches = firstContext.getBean("caffeineCacheManager", CacheManager.class);
        assertSame(localCaches, firstContext.getBean(CacheManager.class));
        assertNull(localCaches.getCache(TimeTrackerCategoryCache.CACHE_NAME).get(owner));
        first.evictAfterCommit(owner);
        second.list(owner, loader);
        second.list(other, loader);
        assertEquals(3, loads.get());
    }

    @Test
    void testRead_空列表命中且有五分钟过期时间() {
        long owner = owner();
        assertTrue(first.list(owner, List::of).isEmpty());
        assertTrue(second.list(owner, () -> fail("空列表应命中缓存")).isEmpty());
        long ttl = redis.getExpire(key(owner));
        assertTrue(ttl > 290 && ttl <= 300, "缓存必须设置五分钟 TTL，实际=" + ttl);
    }

    @Test
    void testSerialization_大整数日期空父级保留且返回实体不会污染缓存() {
        long owner = owner();
        var item = row(owner, "原始配置");
        item.setId(9223372036854775806L);
        item.setTemplateId(9223372036854775805L);
        item.setParentId(null);
        item.setCreateTime(LocalDateTime.of(2026, 10, 28, 12, 30));
        first.list(owner, () -> List.of(item)).getFirst().setIsEnabled(0);
        var restored = second.list(owner, () -> fail("应读取 Redis")).getFirst();
        assertEquals(item.getId(), restored.getId());
        assertEquals(item.getTemplateId(), restored.getTemplateId());
        assertEquals(item.getCreateTime(), restored.getCreateTime());
        assertNull(restored.getParentId());
        assertEquals(1, restored.getIsEnabled());
        restored.setName("个人合并产生的名称");
        assertEquals("原始配置", first.list(owner, () -> fail("应读取 Redis")).getFirst().getName());
    }

    @Test
    void testPublicChanges_共享原始缓存更新后两用户立即重新合并且个人覆盖不污染其他用户() {
        keys.add(key(0L));
        redis.delete(key(0L));
        long ownerA = owner();
        long ownerB = owner();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "category-cache"),
                TimeTrackerCategoryEntity.class);
        var service = spy(new TimeTrackerCategoryServiceImpl(first));
        var parent = row(0, "公共上级");
        var child = row(0, "公共下级"); child.setId(8L); child.setParentId(7L); child.setSortOrder(20);
        var independent = row(0, "另一公共分类"); independent.setId(9L); independent.setSortOrder(30);
        var hiddenParent = row(ownerA, null);
        hiddenParent.setId(100L); hiddenParent.setTemplateId(7L); hiddenParent.setParentId(null);
        hiddenParent.setIsEnabled(0);
        var privateA = row(ownerA, "A个人分类"); privateA.setId(201L); privateA.setSortOrder(40);
        var privateB = row(ownerB, "B个人分类"); privateB.setId(202L); privateB.setSortOrder(40);
        Map<Long, List<TimeTrackerCategoryEntity>> rows = new HashMap<>();
        rows.put(0L, List.of(parent, child, independent));
        rows.put(ownerA, List.of(hiddenParent, privateA));
        rows.put(ownerB, List.of(privateB));
        Map<Long, AtomicInteger> loads = Map.of(0L, new AtomicInteger(),
                ownerA, new AtomicInteger(), ownerB, new AtomicInteger());
        var json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        doAnswer(invocation -> {
            LambdaQueryWrapper<?> query = invocation.getArgument(0);
            query.getSqlSegment();
            long requestedOwner = query.getParamNameValuePairs().values().stream()
                    .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElseThrow();
            loads.get(requestedOwner).incrementAndGet();
            // 模拟数据库每次读取都返回独立实体，原始快照不会受合并过程修改影响。
            return json.convertValue(rows.get(requestedOwner), new TypeReference<List<TimeTrackerCategoryEntity>>() {});
        }).when(service).list(any(Wrapper.class));

        assertEquals(List.of(9L, 201L), ids(service.listUserVisibleCategories(ownerA)));
        assertEquals(List.of(7L, 8L, 9L, 202L), ids(service.listUserVisibleCategories(ownerB)));
        assertEquals(1, loads.get(0L).get());

        parent.setName("新公共上级");
        independent.setName("新公共分类"); independent.setSortOrder(5);
        first.evictAfterCommit(0L);
        var updatedA = service.listUserVisibleCategories(ownerA);
        var updatedB = service.listUserVisibleCategories(ownerB);
        assertEquals("新公共分类", updatedA.getFirst().getName());
        assertEquals(List.of(9L, 7L, 8L, 202L), ids(updatedB));
        assertEquals("新公共上级", updatedB.get(1).getName());

        independent.setIsEnabled(0);
        first.evictAfterCommit(0L);
        assertEquals(List.of(201L), ids(service.listUserVisibleCategories(ownerA)));
        assertEquals(List.of(7L, 8L, 202L), ids(service.listUserVisibleCategories(ownerB)));

        rows.put(0L, List.of());
        first.evictAfterCommit(0L);
        assertEquals(List.of(201L), ids(service.listUserVisibleCategories(ownerA)));
        assertEquals(List.of(202L), ids(service.listUserVisibleCategories(ownerB)));
        assertEquals(4, loads.get(0L).get());
        assertEquals(1, loads.get(ownerA).get());
        assertEquals(1, loads.get(ownerB).get());
    }

    @Test
    void testTransaction_事务内绕过缓存且不发布未提交结果() {
        long owner = owner();
        long uncached = owner();
        first.list(owner, () -> List.of(row(owner, "已提交")));
        transaction(firstContext).executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("未提交", first.list(owner,
                    () -> List.of(row(owner, "未提交"))).getFirst().getName());
            first.list(uncached, List::of);
            assertFalse(redis.hasKey(key(uncached)));
        });
        assertEquals("已提交", second.list(owner, () -> fail("已提交缓存应保留")).getFirst().getName());
        assertFalse(redis.hasKey(key(uncached)));
    }

    @Test
    void testEvict_事务事件经缓存代理在提交后失效() {
        long owner = owner();
        first.list(owner, List::of);
        transaction(firstContext).executeWithoutResult(status -> {
            first.evictAfterCommit(owner);
            assertTrue(redis.hasKey(key(owner)), "提交前必须保留原缓存");
        });
        assertFalse(redis.hasKey(key(owner)), "AFTER_COMMIT 事件必须经 CacheEvict 代理清理 Redis");
        assertEquals("新值", second.list(owner, () -> List.of(row(owner, "新值"))).getFirst().getName());
    }

    @Test
    void testEvict_回滚不触发清理且其他实例仍能命中原缓存() {
        long owner = owner();
        first.list(owner, () -> List.of(row(owner, "原值")));
        transaction(firstContext).executeWithoutResult(status -> {
            first.evictAfterCommit(owner);
            status.setRollbackOnly();
        });
        assertEquals("原值", second.list(owner, () -> fail("回滚不能删除缓存")).getFirst().getName());
    }

    @Test
    void testEvict_无事务时事件立即清理且仅影响对应用户() {
        long owner = owner();
        long other = owner();
        first.list(owner, List::of);
        first.list(other, List::of);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        first.evictAfterCommit(owner);
        assertFalse(redis.hasKey(key(owner)));
        assertTrue(second.list(other, () -> fail("其他用户缓存必须保留")).isEmpty());
    }

    @Test
    void testLoaderFailure_业务异常不吞掉且不重复加载() {
        long owner = owner();
        var loads = new AtomicInteger();
        var failure = new IllegalArgumentException("数据库业务异常");
        var thrown = assertThrows(IllegalArgumentException.class, () -> first.list(owner, () -> {
            loads.incrementAndGet();
            throw failure;
        }));
        assertSame(failure, thrown);
        assertEquals(1, loads.get());
        assertFalse(redis.hasKey(key(owner)));
        assertTrue(second.list(owner, List::of).isEmpty());
    }

    @Test
    void testRedisFailure_缓存读写删除故障回源且提交不会报错() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var manager = context.getBean("cacheManager", RedisCacheManager.class);
            var failed = mock(Cache.class);
            when(failed.getName()).thenReturn(TimeTrackerCategoryCache.CACHE_NAME);
            when(failed.get(any())).thenThrow(new IllegalStateException("Redis read unavailable"));
            doThrow(new IllegalStateException("Redis write unavailable")).when(failed).put(any(), any());
            doThrow(new IllegalStateException("Redis evict unavailable")).when(failed).evict(any());
            doReturn(failed).when(manager).getCache(TimeTrackerCategoryCache.CACHE_NAME);
            var categories = context.getBean(TimeTrackerCategoryCache.class);
            long owner = owner();
            var loads = new AtomicInteger();
            var result = categories.list(owner, () -> {
                loads.incrementAndGet();
                return List.of(row(owner, "数据库结果"));
            });
            assertEquals("数据库结果", result.getFirst().getName());
            assertEquals(1, loads.get());
            assertDoesNotThrow(() -> transaction(context).executeWithoutResult(status ->
                    categories.evictAfterCommit(owner)));
            verify(failed).get(owner);
            verify(failed).put(eq(owner), any());
            verify(failed).evict(owner);
        }
    }

    @Test
    void testRedisFailure_损坏的缓存内容回源并重建() {
        long owner = owner();
        redis.opsForValue().set(key(owner), "invalid-json");
        assertEquals("数据库结果", second.list(owner,
                () -> List.of(row(owner, "数据库结果"))).getFirst().getName());
        assertEquals("数据库结果", first.list(owner, () -> fail("回源后应重建缓存")).getFirst().getName());
    }

    @Test
    void testNamespace_注解缓存不读取旧Hash格式() {
        long owner = owner();
        String oldKey = "timeTrackerCategory:v1:" + owner;
        keys.add(oldKey);
        redis.opsForHash().put(oldKey, "data", "旧版数据");
        assertEquals("当前数据", first.list(owner, () -> List.of(row(owner, "当前数据"))).getFirst().getName());
        assertTrue(redis.hasKey(key(owner)));
        assertEquals("旧版数据", redis.opsForHash().get(oldKey, "data"));
    }

    private TransactionTemplate transaction(AnnotationConfigApplicationContext context) {
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    private long owner() {
        long owner = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        keys.add(key(owner));
        return owner;
    }

    private String key(long owner) { return TimeTrackerCategoryCache.CACHE_NAME + ":" + owner; }

    private List<Long> ids(List<TimeTrackerCategoryEntity> rows) {
        return rows.stream().map(TimeTrackerCategoryEntity::getId).toList();
    }

    private static TimeTrackerCategoryEntity row(long owner, String name) {
        var row = new TimeTrackerCategoryEntity();
        row.setId(7L); row.setUserId(owner); row.setParentId(0L);
        row.setName(name); row.setIsEnabled(1); row.setIsDeleted(0);
        row.setSortOrder(10); row.setIsTrackTime(1); row.setTimeType(1);
        return row;
    }
}
