package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 二级锁菜单路径缓存，供拦截器快速匹配。
 *
 * @author Lys
 * @date 2026/07/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondaryLockMenuCache {

    private final ISysMenuMapper sysMenuMapper;

    private volatile Set<String> lockedPaths = Collections.emptySet();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenuEntity::getIsDeleted, 0);
        wrapper.eq(SysMenuEntity::getStatus, 1);
        wrapper.eq(SysMenuEntity::getSecondaryLock, 1);
        List<SysMenuEntity> list = sysMenuMapper.selectList(wrapper);

        Set<String> paths = new HashSet<>();
        for (SysMenuEntity menu : list) {
            if (menu.getPath() != null && !menu.getPath().isBlank()) {
                paths.add(menu.getPath());
            }
        }
        this.lockedPaths = Collections.unmodifiableSet(paths);
        log.info("二级锁菜单路径缓存已刷新，共 {} 条", paths.size());
    }

    /**
     * 判断请求路径是否匹配二级锁菜单，返回匹配到的菜单路径（前缀匹配），未匹配返回 null。
     */
    public String findMatchedPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        // 按路径长度降序排序，优先精确匹配
        return lockedPaths.stream()
                .filter(p -> requestPath.equals(p) || requestPath.startsWith(p + "/"))
                .max((a, b) -> Integer.compare(a.length(), b.length()))
                .orElse(null);
    }
}
