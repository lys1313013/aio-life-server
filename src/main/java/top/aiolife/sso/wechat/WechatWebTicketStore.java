package top.aiolife.sso.wechat;

import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/** 二维码只携带随机 scene；浏览器持有独立密钥。所有状态迁移原子执行，不延长有效期。 */
@Component
@RequiredArgsConstructor
public class WechatWebTicketStore {
    public static final int EXPIRES_IN = 300;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private final WechatMiniProperties properties;
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            if redis.call('exists', KEYS[1]) == 1 then return 0 end
            redis.call('hset', KEYS[1], 'secret', ARGV[1], 'state', 'WAITING')
            redis.call('expire', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> TRANSITION = new DefaultRedisScript<>("""
            local state = redis.call('hget', KEYS[1], 'state')
            if not state then return 'EXPIRED' end
            local op = ARGV[1]
            if op == 'status' or op == 'exchange' or op == 'revoke' then
              if redis.call('hget', KEYS[1], 'secret') ~= ARGV[2] then return 'DENIED' end
              if op == 'status' then return state end
              if op == 'revoke' then
                if state ~= 'CONSUMED' then redis.call('hset', KEYS[1], 'state', 'CANCELLED') end
                return 'CANCELLED'
              end
              if state ~= 'CONFIRMED' then return state end
              local uid = redis.call('hget', KEYS[1], 'uid')
              redis.call('hset', KEYS[1], 'state', 'CONSUMED')
              return 'USER:' .. uid
            end
            local uid = redis.call('hget', KEYS[1], 'uid')
            if uid and uid ~= ARGV[2] then return 'DENIED' end
            if op == 'scan' and state == 'WAITING' then
              redis.call('hset', KEYS[1], 'uid', ARGV[2], 'state', 'SCANNED')
              return 'SCANNED'
            end
            if op == 'confirm' and state == 'SCANNED' then
              redis.call('hset', KEYS[1], 'state', 'CONFIRMED')
              return 'CONFIRMED'
            end
            if op == 'cancel' and state == 'SCANNED' then
              redis.call('hset', KEYS[1], 'state', 'CANCELLED')
              return 'CANCELLED'
            end
            return state
            """, String.class);

    public static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    public void create(String scene, String browserSecret) {
        Long result = redis.execute(CREATE, List.of(key(scene)), SecureUtil.sha256(browserSecret), String.valueOf(EXPIRES_IN));
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("无法创建扫码登录请求");
    }

    public String browser(String scene, String secret, String operation) {
        if (secret == null || !secret.matches("[a-f0-9]{64}")) throw denied();
        return transition(scene, operation, SecureUtil.sha256(secret));
    }

    public String mobile(String scene, long userId, String operation) {
        return transition(scene, operation, String.valueOf(userId));
    }

    private String transition(String scene, String operation, String credential) {
        String result = redis.execute(TRANSITION, List.of(key(scene)), operation, credential);
        if (result == null || result.equals("DENIED")) throw denied();
        return result;
    }

    private String key(String scene) {
        if (scene == null || !scene.matches("[a-f0-9]{32}")) throw denied();
        return "auth:wechat:web:" + properties.getAppId() + ":" + scene;
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "扫码登录请求无效，请刷新网页二维码");
    }
}
