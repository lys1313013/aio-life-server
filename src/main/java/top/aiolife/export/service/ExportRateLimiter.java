package top.aiolife.export.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** 短时防刷与月度业务配额分离；没有任何每日计数器。 */
@Component
@RequiredArgsConstructor
public class ExportRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "local n=redis.call('incr',KEYS[1]); if n==1 then"
                            + " redis.call('expire',KEYS[1],ARGV[1]) end; return n",
                    Long.class);
    private final StringRedisTemplate redis;

    public void check(String dimension, String subject, int maximum, int seconds) {
        Long count =
                redis.execute(INCREMENT, List.of(key(dimension, subject)), String.valueOf(seconds));
        if (count == null) throw ExportException.unavailable();
        if (count > maximum)
            throw new ExportException(
                    HttpStatus.TOO_MANY_REQUESTS, "EXPORT_RATE_LIMIT", "操作过于频繁，请稍后重试");
    }

    public void clear(String dimension, String subject) {
        redis.delete(key(dimension, subject));
    }

    private String key(String dimension, String subject) {
        return "export:rate:" + dimension + ":" + ExportHash.of(subject);
    }
}
