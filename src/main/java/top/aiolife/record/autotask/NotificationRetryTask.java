package top.aiolife.record.autotask;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.service.FeishuNotificationService;

@Component
@RequiredArgsConstructor
public class NotificationRetryTask {

    private final FeishuNotificationService notificationService;
    private final DistributedLockExecutor locks;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void retryFeishuNotifications() {
        locks.tryRun("notification:feishu:retry:redisson-lock", notificationService::retryPending);
    }
}
