package top.aiolife.core.lock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** 上传与管理员删除共用对象锁；上传持锁到事务完成，Redis 不可用时不执行存储修改。 */
@Component
@RequiredArgsConstructor
public class StorageObjectLock {
    private final RedissonClient redisson;

    public Lease acquire(String bucket, String key) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String lockKey = "aio:storage-object:" + encoder.encodeToString(bucket.getBytes(StandardCharsets.UTF_8))
                + ":" + encoder.encodeToString(key.getBytes(StandardCharsets.UTF_8));
        RLock lock = redisson.getLock(lockKey);
        // 不指定租期，由 watchdog 续期；忙时拒绝删除，不能绕过持锁中的上传。
        if (!lock.tryLock()) throw new ResponseStatusException(HttpStatus.CONFLICT, "文件正在处理中，请稍后重试");
        return () -> { if (lock.isHeldByCurrentThread()) lock.unlock(); };
    }

    public void holdUntilTransactionCompletion(String bucket, String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("文件上传必须在事务中执行");
        Lease lease = acquire(bucket, key);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { lease.close(); }
        });
    }

    public interface Lease extends AutoCloseable {
        @Override void close();
    }
}
