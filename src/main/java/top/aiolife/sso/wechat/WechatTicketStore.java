package top.aiolife.sso.wechat;

import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** 临时凭证仅保存经过服务端验证的身份；GETDEL 保证同一票据只能使用一次。 */
@Component
@RequiredArgsConstructor
public class WechatTicketStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('expire',KEYS[1],60) end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final WechatMiniProperties properties;

    public String issue(Ticket ticket) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = HexFormat.of().formatHex(bytes);
        try {
            redis.opsForValue().set(key(value), mapper.writeValueAsString(ticket), Duration.ofMinutes(5));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法创建微信登录票据");
        }
        return value;
    }

    public Ticket consume(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw expired();
        String json = redis.opsForValue().getAndDelete(key(value));
        if (json == null) throw expired();
        try {
            return mapper.readValue(json, Ticket.class);
        } catch (JsonProcessingException e) {
            throw expired();
        }
    }

    public void rateLimit(String operation, String ip, int limit) {
        Long count = redis.execute(RATE,
                List.of("auth:wechat:rate:" + operation + ":" + SecureUtil.sha256(ip)));
        if (count == null || count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
        }
    }

    private String key(String value) {
        return "auth:wechat:ticket:" + properties.getAppId() + ":" + SecureUtil.sha256(value);
    }

    private ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录票据已失效，请重新微信登录");
    }

    /** phone 非空时票据仅用于绑定原账号，不能再次用于手机号注册。 */
    public record Ticket(String openid, String unionid, String countryCode, String phone) {
        public Ticket withPhone(WechatMiniClient.Phone value) {
            return new Ticket(openid, unionid, value.countryCode(), value.number());
        }
    }
}
