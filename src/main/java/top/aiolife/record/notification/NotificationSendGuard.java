package top.aiolife.record.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.util.concurrent.TimeUnit;

/** LeetCode 同步渠道按日期事件去重，成功标记与互斥锁独立保存。 */
@Component
@RequiredArgsConstructor
public class NotificationSendGuard {

    private final DistributedLockExecutor locks;
    private final RedisUtil redisUtil;

    public void sendOnce(AbstractNotificationSender sender, UserEntity user,
                         NotificationRequest request, String htmlContent) {
        String event = request.bizType() + ":" + request.dedupKey() + ":" + user.getId() + ":" + sender.getChannel();
        locks.tryRun("notification:send:lock:" + event, () -> {
            String sentKey = "notification:sent:" + event;
            if (redisUtil.hasKey(sentKey)) {
                return;
            }
            if (sender.send(user, request.title(), htmlContent, request.textContent())) {
                // 事件键中包含业务日期，覆盖同一天重复触发和短期重放。
                redisUtil.set(sentKey, "1", 2, TimeUnit.DAYS);
            }
        });
    }
}
