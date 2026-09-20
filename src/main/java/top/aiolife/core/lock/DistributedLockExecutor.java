package top.aiolife.core.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 非阻塞分布式互斥；不指定租期，由 Redisson watchdog 续期。 */
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;

    public boolean tryRun(String key, Runnable action) {
        RLock lock = redissonClient.getLock(key);
        if (!lock.tryLock()) {
            return false;
        }
        boolean releaseAfterTransaction = false;
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        unlockIfOwned(lock);
                    }
                });
                releaseAfterTransaction = true;
            }
            action.run();
            return true;
        } finally {
            if (!releaseAfterTransaction) {
                unlockIfOwned(lock);
            }
        }
    }

    private void unlockIfOwned(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
