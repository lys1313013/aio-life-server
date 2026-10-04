package top.aiolife.record.prediction;

import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.service.impl.TimeRecordServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationDataCacheTest {
    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test
    void testCache_隔离用户与查询参数允许缓存空查询并且可失效() {
        var cache = new RecommendationDataCache(15000);
        var loads = new AtomicInteger();
        assertEquals(1, cache.get("records", loads::incrementAndGet, 7L, "today"));
        assertEquals(1, cache.get("records", loads::incrementAndGet, 7L, "today"));
        assertEquals(2, cache.get("records", loads::incrementAndGet, 8L, "today"));
        assertEquals(3, cache.get("records", loads::incrementAndGet, 7L, "yesterday"));
        assertNull(cache.get("empty", () -> { loads.incrementAndGet(); return null; }, 7L));
        assertNull(cache.get("empty", loads::incrementAndGet, 7L));
        assertEquals(4, loads.get());
        cache.invalidate();
        assertEquals(5, cache.get("records", loads::incrementAndGet, 7L, "today"));
    }

    @Test
    void testCache_事务内部查询不进入共享缓存() {
        var cache = new RecommendationDataCache(15000);
        var loads = new AtomicInteger();
        cache.get("records", loads::incrementAndGet, 7L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(2, cache.get("records", loads::incrementAndGet, 7L));
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertEquals(1, cache.get("records", loads::incrementAndGet, 7L));
    }

    @Test
    void testCache_写入立即清理且提交后再次清理() {
        var cache = new RecommendationDataCache(15000);
        var aspect = new RecommendationCacheInvalidationAspect(cache);
        var loads = new AtomicInteger();
        cache.get("records", loads::incrementAndGet, 7L);
        TransactionSynchronizationManager.initSynchronization();
        aspect.invalidate();
        assertEquals(2, cache.get("records", loads::incrementAndGet, 7L));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        assertEquals(3, cache.get("records", loads::incrementAndGet, 7L));
    }

    @Test
    void testCache_失效期间完成的旧查询不会污染新缓存() {
        var cache = new RecommendationDataCache(15000);
        assertEquals(1, cache.get("records", () -> { cache.invalidate(); return 1; }, 7L));
        assertEquals(2, cache.get("records", () -> 2, 7L));
    }

    @Test
    void testCache_实际业务写方法触发切面清理() {
        var cache = new RecommendationDataCache(15000);
        var target = Mockito.mock(TimeRecordServiceImpl.class);
        var factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RecommendationCacheInvalidationAspect(cache));
        TimeRecordServiceImpl proxy = factory.getProxy();
        cache.get("records", () -> 1, 7L);
        proxy.saveTimeRecord(new TimeRecordReq());
        assertEquals(2, cache.get("records", () -> 2, 7L));
        proxy.updateTimeRecord(new TimeRecordReq());
        assertEquals(3, cache.get("records", () -> 3, 7L));
        proxy.removeById("1", 7L);
        assertEquals(4, cache.get("records", () -> 4, 7L));
        proxy.removeByDate(LocalDate.now(), 7L);
        assertEquals(5, cache.get("records", () -> 5, 7L));
    }

    @Test
    void testCache_配置零关闭缓存() {
        var cache = new RecommendationDataCache(0);
        var loads = new AtomicInteger();
        cache.get("records", loads::incrementAndGet, 7L);
        assertEquals(2, cache.get("records", loads::incrementAndGet, 7L));
    }
}
