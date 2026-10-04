package top.aiolife.sso.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class QueryGrantStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('expire',KEYS[1],60) end; return n", Long.class);

    void create(String id, QueryAccessModels.Grant grant) {
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(id), mapper.writeValueAsString(grant),
                    Duration.ofSeconds(300)))) throw new IllegalStateException("查询授权保存失败");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("查询授权保存失败");
        }
    }

    QueryAccessModels.Grant read(String id) {
        String raw = redis.opsForValue().get(key(id));
        if (raw == null) return null;
        try { return mapper.readValue(raw, QueryAccessModels.Grant.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("查询授权状态异常"); }
    }

    void delete(String id) { redis.delete(key(id)); }

    void rateLimit(long userId) {
        Long count = redis.execute(RATE, List.of("aioQuery:issueRate:v1:" + userId));
        if (count == null || count > 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "查询凭据签发过于频繁");
        }
    }

    private String key(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "查询授权不可用");
        }
        return "aioQuery:grant:v1:" + id;
    }
}
