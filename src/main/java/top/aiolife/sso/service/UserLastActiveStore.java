package top.aiolife.sso.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 活跃时间与脏标记原子更新，数据库写入成功后才确认该时间戳。 */
@Component
@RequiredArgsConstructor
public class UserLastActiveStore {
    private static final String PREFIX = "user:last_active:";
    private static final String DIRTY = PREFIX + "dirty";
    private static final DefaultRedisScript<Long> RECORD = new DefaultRedisScript<>("""
            local previous = tonumber(redis.call('get', KEYS[1]))
            local current = tonumber(ARGV[2])
            if previous and (current <= previous or current - previous < tonumber(ARGV[3])) then return 0 end
            redis.call('set', KEYS[1], ARGV[2])
            return redis.call('sadd', KEYS[2], ARGV[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> ACKNOWLEDGE = new DefaultRedisScript<>("""
            local current = redis.call('get', KEYS[1])
            if current == ARGV[2] or (not current and ARGV[2] == '') then
                return redis.call('srem', KEYS[2], ARGV[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public void record(String id, long timestamp, long thresholdMillis) {
        redis.execute(RECORD, List.of(PREFIX + id, DIRTY), id, Long.toString(timestamp), Long.toString(thresholdMillis));
    }

    public List<String> pending(long count) {
        return redis.opsForSet().randomMembers(DIRTY, count);
    }

    public List<String> timestamps(List<String> ids) {
        return redis.opsForValue().multiGet(ids.stream().map(id -> PREFIX + id).toList());
    }

    public void acknowledge(String id, String timestamp) {
        redis.execute(ACKNOWLEDGE, List.of(PREFIX + id, DIRTY), id, timestamp == null ? "" : timestamp);
    }
}
