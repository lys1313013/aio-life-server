package top.aiolife.record.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.AttachmentCollectionMapper;
import top.aiolife.system.mapper.StorageObjectMapper;

/** 未绑定附件先过期，再清理无引用对象；来源成功后不按时间清理。 */
@Service
@RequiredArgsConstructor
public class AttachmentCollectionService {
    private final AttachmentCollectionMapper attachments;
    private final StorageObjectMapper objects;
    private final StorageObjectLock locks;
    private final MinioUtil minio;
    @Transactional
    public void expire() { attachments.expireUnbound(); attachments.purgeFinishedJobs(); }
    @Transactional(rollbackFor=Exception.class)
    public void collectOrphan(String bucket,String key) throws Exception {
        if(!key.matches("objects/[0-9a-f]{2}/[0-9a-f]{64}\\.(jpg|png|gif|bmp|webp|bin)")) return;
        locks.holdUntilTransactionCompletion(bucket,key);
        if(objects.countStoredObject(bucket,key)==0) minio.removeObject(bucket,key);
    }
    @Transactional(rollbackFor=Exception.class)
    public void collect(long id) throws Exception {
        var snapshot=objects.selectById(id);
        if(snapshot==null) return;
        locks.holdUntilTransactionCompletion(snapshot.getBucket(),snapshot.getObjectKey());
        var current=objects.lockPresent(id);
        if(current==null || objects.references(id)>0) return;
        // 先提交对象行删除，再持锁删除字节；回滚不删字节，外部失败交给孤立字节扫描重试。
        objects.deletePhysical(id);
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { minio.removeObject(current.getBucket(),current.getObjectKey()); }
                    catch(Exception e) { /* 数据库已经提交；孤立字节由后续扫描重试清理。 */ }
                }
            });
    }
}
