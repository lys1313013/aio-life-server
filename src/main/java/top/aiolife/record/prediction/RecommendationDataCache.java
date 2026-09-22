package top.aiolife.record.prediction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 推荐链路数据库查询缓存。没有 Jev 结果或完整接口响应缓存。 */
@Component
public class RecommendationDataCache {
    private final Cache<Key, Optional<?>> queries;
    private final AtomicLong generation = new AtomicLong();
    private record Key(long generation, String query, List<?> parameters) {}

    public RecommendationDataCache(@Value("${aio.life.server.typesafe.data-cache-ttl-ms:18000000}") long ttlMs) {
        if (ttlMs < 0) throw new IllegalArgumentException("data-cache-ttl-ms must be non-negative");
        queries = Caffeine.newBuilder().maximumSize(2000)
                .expireAfterWrite(Duration.ofMillis(ttlMs)).build();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String query, Supplier<T> loader, Object... parameters) {
        // 事务内可能读取未提交数据，不将其发布给其他请求。
        if (TransactionSynchronizationManager.isActualTransactionActive()) return loader.get();
        var key = new Key(generation.get(), query, Arrays.asList(parameters));
        return (T) queries.get(key, ignored -> Optional.ofNullable(loader.get())).orElse(null);
    }

    public void invalidate() {
        // 版本隔离保证写入前已开始的慢查询不能在清缓存后重新发布旧结果。
        generation.incrementAndGet();
        queries.invalidateAll();
    }
}
