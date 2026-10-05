package top.aiolife.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.system.pojo.vo.MenuRouteVO;
import top.aiolife.system.pojo.vo.MenuVisualVO;
import top.aiolife.system.pojo.vo.MenuVisualsVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 业务与菜单只在此关联，图标及颜色始终从当前菜单读取。 */
@Service
@RequiredArgsConstructor
public class MenuVisualService {
    public static final MenuVisualVO DEFAULT = new MenuVisualVO(null, null, "lucide:layout-dashboard", null);
    private static final Map<String, List<String>> MENU_PATHS = Map.ofEntries(
        Map.entry("time", List.of("/time/time-tracker", "/time/timeTracker")),
        Map.entry("todo", List.of("/task/todo", "/task-center/todo")),
        Map.entry("think", List.of("/record/think", "/my-hub/think")),
        Map.entry("goal", List.of("/task/goal", "/task-center/goal")),
        Map.entry("anniversary", List.of("/record/anniversary", "/my-hub/anniversary")),
        Map.entry("reading", List.of("/record/read", "/my-hub/read-record")),
        Map.entry("movie", List.of("/record/movie", "/my-hub/movie")),
        Map.entry("membership", List.of("/membership")),
        Map.entry("exercise", List.of("/record/exercise", "/my-hub/exercise")),
        Map.entry("github", List.of("/coding/github")),
        Map.entry("leetcode", List.of("/coding/leetcode")),
        Map.entry("weread", List.of("/record/weread", "/my-hub/weread")),
        Map.entry("shanbay", List.of("/record/shanbay", "/my-hub/shanbay"))
    );
    private static final Map<String, MenuVisualVO> PUBLIC_VISUALS = Map.of(
        "links", new MenuVisualVO(null, null, "lucide:layout-grid", null),
        "shanbay", new MenuVisualVO(null, null, "svg:shanbay", null)
    );

    private final IMenuService menus;
    private final UserMapper users;

    public MenuVisualsVO get(long userId, MenuClient client) {
        var user = users.selectById(userId);
        var roles = user == null || !StringUtils.hasText(user.getRole()) ? List.of("user")
            : Arrays.stream(user.getRole().split(",")).map(String::trim).filter(StringUtils::hasText).toList();
        return resolve(menus.getAccessibleMenuTree(roles, client));
    }

    public MenuVisualsVO resolve(List<MenuRouteVO> tree) {
        List<MenuVisualVO> all = new ArrayList<>();
        flatten(tree, all);
        Map<String, MenuVisualVO> byPath = new LinkedHashMap<>();
        all.forEach(menu -> byPath.putIfAbsent(menu.path(), menu));
        Map<String, MenuVisualVO> cards = new LinkedHashMap<>();
        for (var definition : HomeCardCatalog.ALL) {
            String key = definition.visualKey();
            MenuVisualVO visual = MENU_PATHS.getOrDefault(key, List.of()).stream()
                .map(byPath::get).filter(java.util.Objects::nonNull).findFirst()
                .orElse(PUBLIC_VISUALS.getOrDefault(key, DEFAULT));
            cards.put(definition.cardKey(), visual);
        }
        return new MenuVisualsVO(List.copyOf(all), Map.copyOf(cards));
    }

    public static String normalizeIcon(Object configured) {
        if (!(configured instanceof String value)) return DEFAULT.icon();
        String icon = value.trim();
        return icon.matches("[a-z0-9]+(?:-[a-z0-9]+)*:[a-z0-9]+(?:-[a-z0-9]+)*") ? icon : DEFAULT.icon();
    }

    public static String normalizeColor(String configured) {
        if (configured == null) return null;
        String color = configured.trim();
        return color.matches("#[0-9a-fA-F]{6}") ? color : null;
    }

    private void flatten(List<MenuRouteVO> tree, List<MenuVisualVO> result) {
        if (tree == null) return;
        for (var menu : tree) {
            Object configured = menu.getMeta() == null ? null : menu.getMeta().get("icon");
            String icon = normalizeIcon(configured);
            String color = normalizeColor(menu.getIconColor());
            result.add(new MenuVisualVO(menu.getId() == null ? null : menu.getId().toString(), menu.getPath(), icon, color));
            flatten(menu.getChildren(), result);
        }
    }
}
