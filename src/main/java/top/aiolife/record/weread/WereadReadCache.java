package top.aiolife.record.weread;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/** 有界本地缓存；同一用户、绑定和参数的并发读取合并，失败短暂冷却。 */
@Component
public class WereadReadCache {
    private record Key(String scope, String endpoint, Map<String, Object> params) {}
    private record Entry(JsonNode data, String error, long ttl) {}
    private final Cache<Key, Entry> cache;

    public WereadReadCache() { this(Ticker.systemTicker()); }

    WereadReadCache(Ticker ticker) {
        cache = Caffeine.newBuilder().maximumSize(2000).ticker(ticker)
                .expireAfter(new Expiry<Key, Entry>() {
                    public long expireAfterCreate(Key key, Entry value, long now) { return value.ttl(); }
                    public long expireAfterUpdate(Key key, Entry value, long now, long remaining) { return value.ttl(); }
                    public long expireAfterRead(Key key, Entry value, long now, long remaining) { return remaining; }
                }).build();
    }

    public JsonNode read(String scope, String endpoint, Map<String, Object> params, Supplier<JsonNode> fetch) {
        Entry entry = cache.get(new Key(scope, endpoint, Map.copyOf(params)), ignored -> {
            try {
                JsonNode data = fetch.get();
                if (data == null || !data.isObject()) throw new IllegalStateException("微信读书响应格式异常");
                return new Entry(data.deepCopy(), null, Duration.ofMinutes(5).toNanos());
            } catch (IllegalStateException error) {
                return new Entry(null, error.getMessage(), Duration.ofSeconds(30).toNanos());
            }
        });
        if (entry.error() != null) throw new IllegalStateException(entry.error());
        return entry.data().deepCopy();
    }
}
