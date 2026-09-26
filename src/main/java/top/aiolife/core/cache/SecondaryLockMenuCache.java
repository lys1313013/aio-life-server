package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** 缓存锁定菜单及其实际子菜单，解锁键始终使用原始锁定菜单路径。 */
@Component
@RequiredArgsConstructor
public class SecondaryLockMenuCache {
    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final ISysMenuMapper sysMenuMapper;
    private final Cache<Long, Map<String, Set<String>>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(10_000).build();

    public Set<String> getLockedPaths(long userId) {
        return cache.get(userId, this::loadLockedPaths).keySet();
    }

    public Set<String> findLockedMenus(long userId, Set<String> menus) {
        Set<String> canonical = menus.stream().map(SecondaryLockPolicy::canonicalMenu).collect(Collectors.toSet());
        Set<String> result = new TreeSet<>();
        cache.get(userId, this::loadLockedPaths).forEach((lock, protectedPaths) -> {
            if (protectedPaths.stream().anyMatch(p -> canonical.stream()
                    .anyMatch(m -> SecondaryLockPolicy.under(m, p)))) result.add(lock);
        });
        return result;
    }

    public Set<String> findMatchedPaths(long userId, String requestPath) {
        Set<String> menus = SecondaryLockPolicy.requestMenus(requestPath);
        if (requestPath != null) menus.add(requestPath); // 兼容路径本就一致的菜单
        return findLockedMenus(userId, menus);
    }

    public String findMatchedPath(long userId, String menuPath) {
        return findLockedMenus(userId, Set.of(menuPath)).stream()
                .max(Comparator.comparingInt(String::length)).orElse(null);
    }

    public void evict(long userId) { cache.invalidate(userId); }

    private Map<String, Set<String>> loadLockedPaths(long userId) {
        List<UserSecondaryLockMenuEntity> locks = lockMenuMapper.selectList(
                new LambdaQueryWrapper<UserSecondaryLockMenuEntity>().eq(UserSecondaryLockMenuEntity::getUserId, userId));
        if (locks.isEmpty()) return Map.of();
        List<SysMenuEntity> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getIsDeleted, 0).eq(SysMenuEntity::getStatus, 1));
        Map<Long, SysMenuEntity> byId = menus.stream().collect(Collectors.toMap(SysMenuEntity::getId, m -> m));
        Map<String, Set<String>> result = new HashMap<>();
        for (UserSecondaryLockMenuEntity lock : locks) {
            SysMenuEntity root = byId.get(lock.getMenuId());
            if (root == null || root.getPath() == null || root.getPath().isBlank()) continue;
            Set<String> paths = new HashSet<>();
            for (SysMenuEntity menu : menus) {
                Set<Long> visited = new HashSet<>();
                SysMenuEntity cursor = menu;
                while (cursor != null && visited.add(cursor.getId())) {
                    if (Objects.equals(cursor.getId(), root.getId())) {
                        if (menu.getPath() != null) paths.add(SecondaryLockPolicy.canonicalMenu(menu.getPath()));
                        break;
                    }
                    cursor = byId.get(cursor.getParentId());
                }
            }
            result.put(root.getPath(), Set.copyOf(paths));
        }
        return Map.copyOf(result);
    }
}
