package top.aiolife.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import java.util.List;

/** 菜单参与访问控制，直接读取权威数据，避免提交后 Redis 失效丢失。 */
@Component
@RequiredArgsConstructor
public class MenuAccessReader {
    private final ISysMenuMapper sysMenuMapper;

    public List<SysMenuEntity> getEnabledMenus(MenuClient client) {
        return getMenusForLocks().stream()
                .filter(menu -> client.isVisible(menu.getVisibleClients())).toList();
    }

    public List<SysMenuEntity> getMenusForLocks() {
        return List.copyOf(sysMenuMapper.selectAllForAccessControl());
    }

    public List<SysMenuEntity> getEnabledMenus() {
        return getEnabledMenus(MenuClient.WEB);
    }
}
