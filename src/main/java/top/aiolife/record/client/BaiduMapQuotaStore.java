package top.aiolife.record.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 跨实例共享百度额度状态；Redis 中仅保存 AK 指纹、固定 API 路径和状态码。 */
@Component
public class BaiduMapQuotaStore {
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PREFIX = "footprints:baidu:quota:v1:";
    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public BaiduMapQuotaStore(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    BaiduMapQuotaStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    record Ticket(String dailyKey, String permanentKey, Instant dayEnd, Instant startedAt) {}

    Ticket beforeQuery(String serviceKey, String path) {
        var now = clock.instant();
        var day = now.atZone(QUOTA_ZONE).toLocalDate();
        String base = PREFIX + fingerprint(serviceKey) + ":" + path;
        var ticket = new Ticket(base + ":day:" + day, base + ":permanent",
                day.plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant(), now);
        try {
            var states = redis.opsForValue().multiGet(List.of(ticket.dailyKey(), ticket.permanentKey()));
            if (states == null) throw stateUnavailable();
            if (states.stream().anyMatch(value -> value != null)) throw new MapQuotaExceededException();
            return ticket;
        } catch (DataAccessException exception) {
            // 状态未知时转腾讯，避免 Redis 故障期间不断请求已耗尽的百度；不泄露连接信息。
            throw stateUnavailable();
        }
    }

    void recordExhausted(Ticket ticket, int status) {
        boolean permanent = status == 301;
        var end = permanent ? ticket.startedAt().plus(Duration.ofHours(24)) : ticket.dayEnd();
        var ttl = Duration.between(clock.instant(), end);
        // 跨日迟到响应不能把昨天的日配额错误写到今天。
        if (ttl.isNegative() || ttl.isZero()) return;
        try {
            redis.opsForValue().setIfAbsent(permanent ? ticket.permanentKey() : ticket.dailyKey(),
                    Integer.toString(status), ttl);
        } catch (DataAccessException exception) {
            // 写入失败也允许本次转腾讯；后续读取失败同样直接转腾讯。
            throw stateUnavailable();
        }
    }

    private MapFallbackException stateUnavailable() {
        return new MapFallbackException("暂时无法读取或保存百度地图额度状态，请稍后重试或手工选点");
    }

    private String fingerprint(String serviceKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(serviceKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
