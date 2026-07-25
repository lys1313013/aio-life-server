package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户二级锁菜单路径缓存，供拦截器按 userId 快速匹配。
 *
 * @author Lys
 * @date 2026/07/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondaryLockMenuCache {

    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final ISysMenuMapper sysMenuMapper;

    private final Cache<Long, Set<String>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    /**
     * 获取用户锁定的菜单路径集合。
     */
    public Set<String> getLockedPaths(long userId) {
        return cache.get(userId, this::loadLockedPaths);
    }

    /**
     * 判断请求路径是否匹配用户的二级锁菜单，返回匹配到的菜单路径，未匹配返回 null。
     */
    public String findMatchedPath(long userId, String requestPath) {
        Set<String> paths = getLockedPaths(userId);
        if (paths.isEmpty() || requestPath == null || requestPath.isBlank()) {
            return null;
        }
        return paths.stream()
                .filter(p -> requestPath.equals(p) || requestPath.startsWith(p + "/"))
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }

    /**
     * 清除指定用户的缓存，用户修改锁定菜单后调用。
     */
    public void evict(long userId) {
        cache.invalidate(userId);
    }

    private Set<String> loadLockedPaths(long userId) {
        LambdaQueryWrapper<UserSecondaryLockMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSecondaryLockMenuEntity::getUserId, userId);
        List<UserSecondaryLockMenuEntity> list = lockMenuMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> menuIds = list.stream()
                .map(UserSecondaryLockMenuEntity::getMenuId)
                .toList();
        LambdaQueryWrapper<SysMenuEntity> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(SysMenuEntity::getId, menuIds);
        menuWrapper.eq(SysMenuEntity::getIsDeleted, 0);
        menuWrapper.eq(SysMenuEntity::getStatus, 1);
        List<SysMenuEntity> menus = sysMenuMapper.selectList(menuWrapper);

        return menus.stream()
                .map(SysMenuEntity::getPath)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
