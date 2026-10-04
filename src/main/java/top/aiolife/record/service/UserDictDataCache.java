package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;

/** 分别缓存公共字典和用户覆盖记录，公共值变更无需遍历所有用户的缓存。 */
@Service
@Slf4j
public class UserDictDataCache {
    public static final String CACHE_NAME = "userDictData:v1";

    private final UserDictDataMapper mapper;
    private final CacheManager cacheManager;

    public UserDictDataCache(UserDictDataMapper mapper, @Qualifier("cacheManager") CacheManager cacheManager) {
        this.mapper = mapper;
        this.cacheManager = cacheManager;
    }

    // 银行卡标签由独立业务维护，保持实时查询；事务中不读取或写入共享缓存。
    @Cacheable(cacheNames = CACHE_NAME, cacheManager = "cacheManager", key = "#userId + ':' + #dictType",
            condition = "#dictType != null && !#dictType.isBlank() && #dictType != 'bank_card_tag'"
                    + " && !T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()")
    public List<UserDictDataEntity> list(Long userId, String dictType) {
        return mapper.selectList(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getUserId, userId)
                .eq(UserDictDataEntity::getDictType, dictType)
                .eq(UserDictDataEntity::getIsDeleted, 0));
    }

    /** 修改类型时同时清理旧类型和新类型；回滚时保留原缓存。 */
    public void evictAfterCommit(Long userId, String... dictTypes) {
        List<String> types = Arrays.stream(dictTypes).filter(Objects::nonNull).distinct().toList();
        Runnable evict = () -> {
            for (String type : types) {
                try {
                    var cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null) cache.evict(userId + ":" + type);
                } catch (RuntimeException exception) {
                    log.warn("User dictionary cache eviction failed: userId={}, dictType={}", userId, type, exception);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { evict.run(); }
            });
        } else {
            evict.run();
        }
    }
}
