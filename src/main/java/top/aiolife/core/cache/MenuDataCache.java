package top.aiolife.core.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import java.util.List;
import java.util.Objects;
import top.aiolife.system.service.MenuClient;

/** 菜单参与访问控制，直接读取权威数据，避免提交后 Redis 失效丢失。保留原类名以兼容调用方。 */
@Component
@RequiredArgsConstructor
public class MenuDataCache {
    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final ISysMenuMapper sysMenuMapper;

    public List<Long> getLockedMenuIds(long userId) {
        return lockMenuMapper.selectForAccessControl(userId)
                .stream().map(UserSecondaryLockMenuEntity::getMenuId).filter(Objects::nonNull).distinct().toList();
    }

    public List<SysMenuEntity> getEnabledMenus(MenuClient client) {
        return client == MenuClient.WEB ? getEnabledMenus() : getMenusForLocks().stream()
                .filter(menu -> Objects.equals(menu.getMobileStatus(), 1)).toList();
    }

    public List<SysMenuEntity> getMenusForLocks() {
        return List.copyOf(sysMenuMapper.selectAllForAccessControl());
    }

    public List<SysMenuEntity> getEnabledMenus() {
        return List.copyOf(sysMenuMapper.selectEnabledForAccessControl());
    }
}
