package top.aiolife.record.prediction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.system.service.IntegrationSystemConfigService;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 推荐链路数据库查询缓存。没有 Jev 结果或完整接口响应缓存。 */
@Component
public class RecommendationDataCache {
    private final LongSupplier ttl;
    private volatile CacheState state;
    private record CacheState(long ttlMs, Cache<Key, Optional<?>> queries) {}
    private final AtomicLong generation = new AtomicLong();
    private record Key(long generation, String query, List<?> parameters) {}

    @Autowired
    public RecommendationDataCache(IntegrationSystemConfigService configuration) {
        this(() -> configuration.typesafe().dataCacheTtlMs());
    }

    public RecommendationDataCache(long ttlMs) {
        this(() -> ttlMs);
        if (ttlMs < 0) throw new IllegalArgumentException("data-cache-ttl-ms must be non-negative");
    }

    RecommendationDataCache(LongSupplier ttl) { this.ttl = ttl; }

    private synchronized CacheState cache(long ttlMs) {
        if (state == null || state.ttlMs() != ttlMs) {
            // 换实例隔离旧请求；各节点下一次读取配置时均生效，无需广播本地缓存失效。
            state = new CacheState(ttlMs, Caffeine.newBuilder().maximumSize(2000)
                    .expireAfterWrite(Duration.ofMillis(ttlMs)).build());
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String query, Supplier<T> loader, Object... parameters) {
        // 事务内可能读取未提交数据，不将其发布给其他请求。
        if (TransactionSynchronizationManager.isActualTransactionActive()) return loader.get();
        long ttlMs = ttl.getAsLong();
        CacheState current = cache(ttlMs);
        if (ttlMs == 0) return loader.get();
        var key = new Key(generation.get(), query, Arrays.asList(parameters));
        return (T) current.queries().get(key, ignored -> Optional.ofNullable(loader.get())).orElse(null);
    }

    public void invalidate() {
        // 版本隔离保证写入前已开始的慢查询不能在清缓存后重新发布旧结果。
        generation.incrementAndGet();
        CacheState current = state;
        if (current != null) current.queries().invalidateAll();
    }
}
