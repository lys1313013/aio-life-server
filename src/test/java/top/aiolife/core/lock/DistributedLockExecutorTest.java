package top.aiolife.core.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributedLockExecutorTest {
    private final RedissonClient client = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final DistributedLockExecutor executor = new DistributedLockExecutor(client);
    private final Runnable action = mock(Runnable.class);

    private void acquired() {
        when(client.getLock("key")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void tryRun_竞争失败不执行也不解锁() {
        when(client.getLock("key")).thenReturn(lock);
        assertFalse(executor.tryRun("key", action));
        verifyNoInteractions(action);
        verify(lock, never()).unlock();
    }

    @Test
    void tryRun_成功执行并释放自己的锁() {
        acquired();
        assertTrue(executor.tryRun("key", action));
        verify(action).run();
        verify(lock).unlock();
    }

    @Test
    void tryRun_业务抛错也释放锁并保留异常() {
        acquired();
        var failure = new IllegalArgumentException("failure");
        doThrow(failure).when(action).run();
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> executor.tryRun("key", action)));
        verify(lock).unlock();
    }

    @Test
    void tryRun_失去锁所有权不能解锁其他实例() {
        acquired();
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        executor.tryRun("key", action);
        verify(lock, never()).unlock();
    }

    @Test
    void tryRun_事务提交后才解锁() {
        verifyTransactionRelease(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    void tryRun_事务回滚后也解锁() {
        verifyTransactionRelease(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void verifyTransactionRelease(int status) {
        acquired();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        executor.tryRun("key", action);
        verify(lock, never()).unlock();
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
        verify(lock).unlock();
    }
}
