package top.aiolife.sso.autotask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 将 Redis 中的最后活跃时间批量同步到 DB（降低写库频率）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLastActiveSyncTask {

    private static final String LAST_ACTIVE_KEY_PREFIX = "user:last_active:";
    private static final String DIRTY_SET_KEY = "user:last_active:dirty";

    private final RedisUtil redisUtil;
    private final UserMapper userMapper;

    @Value("${spring.user.last-active.sync-batch-size:200}")
    private long batchSize;

    /**
     * 定时批量同步（默认 60s 一次）
     */
    @Scheduled(fixedDelayString = "${spring.user.last-active.sync-delay-ms:60000}")
    public void syncToDb() {
        try {
            long count = Math.max(0, batchSize);
            if (count == 0) {
                return;
            }

            List<String> userIds = redisUtil.sPop(DIRTY_SET_KEY, count);
            if (userIds == null || userIds.isEmpty()) {
                return;
            }

            List<String> keys = userIds.stream()
                    .filter(Objects::nonNull)
                    .map(id -> LAST_ACTIVE_KEY_PREFIX + id)
                    .toList();
            List<String> values = redisUtil.multiGet(keys);
            if (values == null || values.isEmpty()) {
                return;
            }

            ZoneId zoneId = ZoneId.systemDefault();
            int size = Math.min(userIds.size(), values.size());
            for (int i = 0; i < size; i++) {
                Long userId = parseLong(userIds.get(i));
                Long ts = parseLong(values.get(i));
                if (userId == null || ts == null) {
                    continue;
                }

                LocalDateTime lastActiveTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zoneId);
                UserEntity entity = new UserEntity();
                entity.setId(userId);
                entity.setLastActiveTime(lastActiveTime);
                userMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("同步用户最后活跃时间到数据库失败", e);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}

