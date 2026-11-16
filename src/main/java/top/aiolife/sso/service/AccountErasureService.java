package top.aiolife.sso.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.export.storage.ExportArtifactStore;
import top.aiolife.relationship.repository.PersonRepository;
import top.aiolife.record.service.TimeTrackerCategoryCache;
import top.aiolife.sso.mapper.AccountErasureMapper;

/** 账号行锁防止多实例重复清理；外部删除幂等，失败保留任务和资源索引供重试。 */
@Service
@RequiredArgsConstructor
public class AccountErasureService {
    private final AccountErasureMapper mapper;
    private final MinioUtil minio;
    private final MinioConfig config;
    private final StorageObjectLock locks;
    private final ExportArtifactStore exports;
    private final ObjectProvider<PersonRepository> people;
    private final CacheManager caches;
    private final TimeTrackerCategoryCache categoryCache;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void erase(long userId) throws Exception {
        var user = mapper.lockPending(userId);
        if (user == null) return;
        if (mapper.runningExports(userId, LocalDateTime.now(ZoneId.of("Asia/Shanghai"))) > 0)
            throw new IllegalStateException("等待导出任务停止");
        for (String object : mapper.exportObjects(userId)) exports.delete(object);
        // 字节由统一回收器在提交后清理，事务回滚不会破坏仍然有效的附件。
        var repository = people.getIfAvailable();
        if (repository != null) repository.eraseByUserId(userId);
        mapper.purgePersonalRecords(userId, user.getEmail());
        mapper.scrubObjectAudit(userId);
        mapper.scrubSourceAudit(userId);
        mapper.scrubAttachmentAudit(userId);
        if (mapper.finish(userId) != 1) throw new IllegalStateException("注销清理状态已变化");
        categoryCache.evictAfterCommit(userId);
        for (String name : caches.getCacheNames()) {
            var cache = caches.getCache(name);
            if (cache != null) cache.evict(userId);
        }
    }
}
