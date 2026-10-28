package top.aiolife.record.service;

import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;

/** 公共分类与个人原始配置分别缓存，合并结果不跨用户共享。 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = TimeTrackerCategoryCache.CACHE_NAME, cacheManager = "cacheManager")
public class TimeTrackerCategoryCache {
    public static final String CACHE_NAME = "timeTrackerCategory:v2";
    private final ApplicationEventPublisher events;

    // 独立 Bean 供业务服务通过代理调用；写事务内查询不读取或发布共享缓存。
    @Cacheable(key = "#ownerId", condition =
            "!T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()")
    public List<TimeTrackerCategoryEntity> list(Long ownerId, Supplier<List<TimeTrackerCategoryEntity>> loader) {
        return loader.get();
    }

    /** 个人变更传用户 ID，公共变更传 0；回滚保留原缓存，提交后同步失效。 */
    public void evictAfterCommit(Long ownerId) {
        events.publishEvent(new CategoryChanged(ownerId));
    }

    // 事件由 Spring 经代理调用，避免同类自调用绕过 @CacheEvict；无事务时立即失效。
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @CacheEvict(key = "#event.ownerId")
    public void onCategoryChanged(CategoryChanged event) { }

    public record CategoryChanged(Long ownerId) { }
}
