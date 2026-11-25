package top.aiolife.sso.autotask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import top.aiolife.sso.service.UserLastActiveStore;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 将 Redis 中的最后活跃时间批量同步到 DB（降低写库频率）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLastActiveSyncTask {

    private final UserLastActiveStore active;
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

            List<String> userIds = active.pending(count);
            if (userIds == null || userIds.isEmpty()) return;
            List<String> values = active.timestamps(userIds);
            if (values == null || values.size() != userIds.size()) return;

            ZoneId zoneId = ZoneId.systemDefault();
            for (int i = 0; i < userIds.size(); i++) {
                String id = userIds.get(i);
                String value = values.get(i);
                try {
                    flush(id, value, zoneId);
                } catch (Exception e) {
                    log.warn("同步用户最后活跃时间失败 userId={}", id, e);
                }
            }
        } catch (Exception e) {
            log.warn("同步用户最后活跃时间到数据库失败", e);
        }
    }

    private void flush(String id, String value, ZoneId zoneId) {
        Long userId = parseLong(id);
        Long timestamp = parseLong(value);
        if (userId != null && timestamp != null) {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
            userMapper.update(Wrappers.<UserEntity>lambdaUpdate()
                    .eq(UserEntity::getId, userId)
                    .and(query -> query.isNull(UserEntity::getLastActiveTime)
                            .or().lt(UserEntity::getLastActiveTime, time))
                    .set(UserEntity::getLastActiveTime, time));
        }
        active.acknowledge(id, value);
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

