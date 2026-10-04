package top.aiolife.sso.qr;

import cn.hutool.crypto.SecureUtil;
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

/** 使用完整快照 CAS，跨实例的扫码、确认、取消与兑换均只能有一个胜者。 */
@Component
@RequiredArgsConstructor
public class QrLoginStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
            if ARGV[2] == '' then redis.call('del', KEYS[1])
            elseif ARGV[3] == '0' then redis.call('set', KEYS[1], ARGV[2], 'KEEPTTL')
            else redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('expire',KEYS[1],60) end; return n", Long.class);

    public record Snapshot(String raw, QrLoginModels.Ticket ticket) {}

    public void create(String id, QrLoginModels.Ticket ticket) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(id), encode(ticket), Duration.ofSeconds(120)))) {
            throw new IllegalStateException("无法创建扫码登录请求");
        }
    }

    public Snapshot read(String id) {
        String raw = redis.opsForValue().get(key(id));
        if (raw == null) return null;
        try {
            return new Snapshot(raw, mapper.readValue(raw, QrLoginModels.Ticket.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("扫码登录状态异常");
        }
    }

    public boolean replace(String id, Snapshot previous, QrLoginModels.Ticket next, int ttl) {
        return Long.valueOf(1).equals(redis.execute(CAS, List.of(key(id)), previous.raw(),
                next == null ? "" : encode(next), Integer.toString(ttl)));
    }

    public void rateLimit(String operation, String subject, int limit) {
        Long count = redis.execute(RATE, List.of("auth:qr:rate:" + operation + ":" + SecureUtil.sha256(subject)));
        if (count == null || count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
        }
    }

    private String key(String id) {
        if (id == null || !id.matches(QrLoginModels.SECRET)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码无效，请重新扫码");
        }
        return "auth:qr:ticket:" + id;
    }

    private String encode(QrLoginModels.Ticket ticket) {
        try { return mapper.writeValueAsString(ticket); }
        catch (JsonProcessingException e) { throw new IllegalStateException("无法保存扫码登录状态"); }
    }
}
