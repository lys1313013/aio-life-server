package top.aiolife.core.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/18 23:47
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 设置Redis键值
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置对象到Redis（JSON序列化）
     */
    public void setObject(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (JsonProcessingException e) {
            log.error("Redis序列化失败", e);
        }
    }

    /**
     * 从Redis获取对象（JSON反序列化）
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Redis反序列化失败", e);
            return null;
        }
    }

    /**
     * 获取Redis键值
     *
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 设置Redis键值
     *
     * @param key     键
     * @param value   值
     * @param timeout 超时时间
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 仅在 key 不存在时写入。
     */
    public boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    /**
     * 仅在锁值仍属于当前持有者时原子释放锁。
     */
    public boolean unlock(String key, String value) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
        Long result = stringRedisTemplate.execute(script, List.of(key), value);
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 判断是否存在key
     *
     * @param key key
     * @return 是否存在key
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 删除key
     *
     * @param key key
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 自增
     *
     * @param key   key
     * @param delta 增量
     * @return 自增后的值
     */
    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 设置过期时间
     *
     * @param key     key
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /**
     * Set 添加成员
     */
    public Long sAdd(String key, String... members) {
        return stringRedisTemplate.opsForSet().add(key, members);
    }

    /**
     * Set 弹出成员（会移除）
     */
    public List<String> sPop(String key, long count) {
        if (count <= 0) {
            return List.of();
        }
        return stringRedisTemplate.opsForSet().pop(key, count);
    }

    /**
     * 批量获取值
     */
    public List<String> multiGet(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return stringRedisTemplate.opsForValue().multiGet(keys);
    }
}
