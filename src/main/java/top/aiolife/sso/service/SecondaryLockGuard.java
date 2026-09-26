package top.aiolife.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.core.cache.SecondaryLockPolicy;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;

import java.util.Set;

/** 在当前请求/工具身份下校验所有关联业务锁，避免通过聚合或 MCP 旁路。 */
@Component
@RequiredArgsConstructor
public class SecondaryLockGuard {
    private final SecondaryLockMenuCache menuCache;
    private final RedisUtil redisUtil;

    public void checkMenus(long userId, String... menus) {
        checkLocks(userId, menuCache.findLockedMenus(userId, Set.of(menus)));
    }

    public void checkTool(String name) {
        long userId = StpUtil.getLoginIdAsLong();
        checkLocks(userId, menuCache.findLockedMenus(userId, SecondaryLockPolicy.toolMenus(name)));
    }

    private void checkLocks(long userId, Set<String> locks) {
        for (String path : locks) {
            if (!redisUtil.hasKey(SecondaryLockInterceptor.unlockKey(userId, path))) {
                throw new SecondaryLockRequiredException(path);
            }
        }
    }
}
