package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 菜单及用户锁配置的共享 Redis 缓存；角色过滤仍在每次请求时执行。 */
@Component
@RequiredArgsConstructor
public class MenuDataCache {
    private static final String PREFIX = "aio:menu-cache:v1:";
    private static final long TTL_HOURS = 5;
    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final ISysMenuMapper sysMenuMapper;
    private final RedisUtil redisUtil;

    public List<Long> getLockedMenuIds(long userId) {
        Long[] ids = load("locks:" + userId, Long[].class, () -> lockMenuMapper.selectList(
                        new LambdaQueryWrapper<UserSecondaryLockMenuEntity>().eq(UserSecondaryLockMenuEntity::getUserId, userId))
                .stream().map(UserSecondaryLockMenuEntity::getMenuId).filter(Objects::nonNull).distinct().toArray(Long[]::new));
        return List.copyOf(Arrays.asList(ids));
    }

    public List<SysMenuEntity> getEnabledMenus() {
        SysMenuEntity[] menus = load("enabled", SysMenuEntity[].class, () -> sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenuEntity>().eq(SysMenuEntity::getIsDeleted, 0)
                        .eq(SysMenuEntity::getStatus, 1).orderByAsc(SysMenuEntity::getSort).orderByAsc(SysMenuEntity::getId))
                .toArray(SysMenuEntity[]::new));
        return List.copyOf(Arrays.asList(menus));
    }

    public void evictLockedMenuIds(long userId) { evict("locks:" + userId); }
    public void evictMenus() { evict("enabled"); }

    private <T> T load(String scope, Class<T> type, Supplier<T> loader) {
        // 事务内绕过共享缓存，避免把未提交的数据或旧事务快照写入 Redis。
        if (TransactionSynchronizationManager.isActualTransactionActive()) return loader.get();
        String version = redisUtil.get(PREFIX + scope + ":version");
        String key = PREFIX + scope + ":data:" + (version == null ? "0" : version);
        T cached = redisUtil.getObject(key, type);
        if (cached != null) return cached;
        T data = loader.get();
        // 空数组也缓存。版本隔离保证失效期间的旧查询无法覆盖新配置。
        redisUtil.setObject(key, data, TTL_HOURS, TimeUnit.HOURS);
        return data;
    }

    private void evict(String scope) {
        Runnable invalidate = () -> redisUtil.set(PREFIX + scope + ":version", UUID.randomUUID().toString());
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
            });
        } else {
            invalidate.run();
        }
        // 版本标识保留；旧版本的数据键由 5 小时 TTL 自动回收，不扫描 Redis。
    }
}
