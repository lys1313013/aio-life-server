package top.aiolife.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.util.SecondaryLockSession;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 在当前请求/工具身份下校验所有关联业务锁，避免通过聚合或 MCP 旁路。 */
@Component
@RequiredArgsConstructor
public class SecondaryLockGuard {
    private final SecondaryLockMenuResolver menuResolver;
    private final RedisUtil redisUtil;

    public void checkMenus(long userId, String... menus) {
        checkCurrentSession(userId, menuResolver.findLockedMenus(userId, Set.of(menus)));
    }

    /** 用于已经校验了来源会话的调用链，例如查询授权和私有附件。 */
    public void checkMenusForSession(long userId, String sessionHash, String... menus) {
        checkLocks(userId, sessionHash, menuResolver.findLockedMenus(userId, Set.of(menus)));
    }

    public Set<String> accessibleGroups(long userId, String sessionHash, Map<String, Set<String>> menusByGroup) {
        var lockedPaths = menuResolver.loadLockedPaths(userId);
        var blockedPaths = lockedPaths.keySet().stream()
                .filter(path -> !SecondaryLockSession.isUnlocked(redisUtil, userId, sessionHash, path))
                .collect(Collectors.toMap(path -> path, lockedPaths::get));
        return menusByGroup.entrySet().stream()
                .filter(entry -> menuResolver.findLockedMenus(blockedPaths, entry.getValue()).isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void checkTool(String name) {
        long userId = StpUtil.getLoginIdAsLong();
        checkCurrentSession(userId, menuResolver.findLockedMenus(userId, SecondaryLockPolicy.toolMenus(name)));
    }

    private void checkCurrentSession(long userId, Set<String> locks) {
        if (!locks.isEmpty()) checkLocks(userId, SecondaryLockSession.currentHash(), locks);
    }

    private void checkLocks(long userId, String sessionHash, Set<String> locks) {
        for (String path : locks) {
            if (!SecondaryLockSession.isUnlocked(redisUtil, userId, sessionHash, path)) {
                throw new SecondaryLockRequiredException(path);
            }
        }
    }
}
