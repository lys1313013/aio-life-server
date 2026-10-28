package top.aiolife.sso.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import top.aiolife.system.service.MenuAccessReader;

import java.util.*;
import java.util.stream.Collectors;

/** 根据当前权威数据解析锁定菜单及其子菜单，解锁键始终使用原始锁定菜单路径。 */
@Component
@RequiredArgsConstructor
public class SecondaryLockMenuResolver {
    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final MenuAccessReader menuAccessReader;

    public List<Long> getLockedMenuIds(long userId) {
        return lockMenuMapper.selectForAccessControl(userId)
                .stream().map(UserSecondaryLockMenuEntity::getMenuId).filter(Objects::nonNull).distinct().toList();
    }

    public Set<String> getLockedPaths(long userId) {
        return loadLockedPaths(userId).keySet();
    }

    public Set<String> findLockedMenus(long userId, Set<String> menus) {
        Set<String> canonical = menus.stream().map(SecondaryLockPolicy::canonicalMenu).collect(Collectors.toSet());
        Set<String> result = new TreeSet<>();
        loadLockedPaths(userId).forEach((lock, protectedPaths) -> {
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


    private Map<String, Set<String>> loadLockedPaths(long userId) {
        List<Long> locks = getLockedMenuIds(userId);
        if (locks.isEmpty()) return Map.of();
        List<SysMenuEntity> menus = menuAccessReader.getMenusForLocks();
        Map<Long, SysMenuEntity> byId = menus.stream().collect(Collectors.toMap(SysMenuEntity::getId, m -> m));
        Map<String, Set<String>> result = new HashMap<>();
        for (Long menuId : locks) {
            SysMenuEntity root = byId.get(menuId);
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
