package top.aiolife.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.mapper.UserMenuHiddenMapper;
import top.aiolife.system.pojo.entity.UserMenuHiddenEntity;
import top.aiolife.system.pojo.vo.MenuRouteVO;
import top.aiolife.system.pojo.vo.UserMenuPreferenceVO;
import top.aiolife.system.pojo.vo.UserMenuPreferenceVO.Menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 个人菜单显示偏好，不改变路由、角色权限、菜单锁和快捷导航。 */
@Service
@RequiredArgsConstructor
public class UserMenuPreferenceService {
    private final IMenuService menuService;
    private final UserMapper userMapper;
    private final UserMenuHiddenMapper hiddenMapper;

    public UserMenuPreferenceVO get(long userId) {
        List<Menu> menus = configurableMenus(userId);
        Set<Long> allowed = leafIds(menus);
        List<String> hidden = hiddenMapper.selectHiddenMenuIds(userId).stream()
                .filter(allowed::contains).map(String::valueOf).toList();
        return new UserMenuPreferenceVO(menus, hidden);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserMenuPreferenceVO save(long userId, List<Long> menuIds) {
        lockUser(userId);
        List<Menu> menus = configurableMenus(userId);
        if (menuIds == null || menuIds.size() > 2000 || menuIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("菜单 ID 不合法");
        }
        Set<Long> desired = new HashSet<>(menuIds);
        if (!leafIds(menus).containsAll(desired)) {
            throw new IllegalArgumentException("包含无权配置、已停用或不可隐藏的菜单，请刷新后重试");
        }
        Set<Long> current = new HashSet<>(hiddenMapper.selectHiddenMenuIds(userId));
        if (!current.equals(desired)) {
            hiddenMapper.physicalDeleteByUserId(userId);
            for (Long menuId : desired.stream().sorted().toList()) {
                UserMenuHiddenEntity entity = new UserMenuHiddenEntity();
                entity.setUserId(userId);
                entity.setMenuId(menuId);
                entity.fillCreateCommonField(userId);
                hiddenMapper.insert(entity);
            }
        }
        return new UserMenuPreferenceVO(menus, desired.stream().sorted().map(String::valueOf).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserMenuPreferenceVO reset(long userId) {
        lockUser(userId);
        hiddenMapper.physicalDeleteByUserId(userId);
        return new UserMenuPreferenceVO(configurableMenus(userId), List.of());
    }

    private void lockUser(long userId) {
        if (hiddenMapper.lockUser(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private List<Menu> configurableMenus(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<String> roles = StringUtils.hasText(user.getRole())
                ? List.of(user.getRole().split(",")).stream().map(String::trim)
                    .filter(StringUtils::hasText).toList()
                : List.of("user");
        return toPreferences(menuService.getAccessibleMenuTree(roles));
    }

    private List<Menu> toPreferences(List<MenuRouteVO> routes) {
        List<Menu> result = new ArrayList<>();
        for (MenuRouteVO route : routes) {
            Map<String, Object> meta = route.getMeta() == null ? Map.of() : route.getMeta();
            // 保留个人中心恢复入口，不向用户暴露管理员已隐藏的菜单。
            if (route.getId() == null || Boolean.TRUE.equals(meta.get("hideInMenu"))
                    || "Profile".equals(route.getName()) || "/profile".equals(route.getPath())) {
                continue;
            }
            List<Menu> children = Boolean.TRUE.equals(meta.get("hideChildrenInMenu"))
                    ? List.of() : toPreferences(route.getChildren() == null ? List.of() : route.getChildren());
            boolean directory = !StringUtils.hasText(route.getComponent()) || "BasicLayout".equals(route.getComponent());
            if (children.isEmpty() && directory && !Boolean.TRUE.equals(meta.get("hideChildrenInMenu"))) {
                continue;
            }
            Object title = meta.get("title");
            result.add(new Menu(route.getId().toString(), title == null ? route.getName() : title.toString(), children));
        }
        return result;
    }

    private Set<Long> leafIds(List<Menu> menus) {
        Set<Long> ids = new HashSet<>();
        for (Menu menu : menus) {
            if (menu.children().isEmpty()) {
                ids.add(Long.valueOf(menu.id()));
            } else {
                ids.addAll(leafIds(menu.children()));
            }
        }
        return ids;
    }
}
