package top.aiolife.system.service;

import org.junit.jupiter.api.Test;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.vo.MenuRouteVO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuVisualServiceTest {
    private final IMenuService menus = mock(IMenuService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final MenuVisualService service = new MenuVisualService(menus, users);

    private MenuRouteVO menu(long id, String path, String icon, String color) {
        var result = new MenuRouteVO();
        result.setId(id);
        result.setPath(path);
        result.setMeta(Map.of("icon", icon, "title", "名称随时可修改"));
        result.setIconColor(color);
        return result;
    }

    @Test void 同一业务多张卡片引用当前菜单且保留字符串大整数ID() {
        var exercise = menu(9007199254740993L, "/record/exercise", "lucide:activity", "#123456");
        var tree = List.of(exercise);
        var first = service.resolve(tree);
        assertEquals(first.cards().get("overview.exercise"), first.cards().get("section.exercise"));
        assertEquals("9007199254740993", first.cards().get("section.exercise").menuId());
        assertEquals("lucide:activity", first.cards().get("overview.exercise").icon());
        exercise.setMeta(Map.of("icon", "mdi:run"));
        exercise.setIconColor("#abcdef");
        var updated = service.resolve(tree);
        assertEquals("mdi:run", updated.cards().get("overview.exercise").icon());
        assertEquals("#abcdef", updated.cards().get("section.exercise").iconColor());
        exercise.setIconColor(null);
        assertNull(service.resolve(tree).cards().get("section.exercise").iconColor());
    }

    @Test void 阅读记录与微信读书按实际业务区分且支持嵌套和旧路由() {
        var parent = menu(1, "/my-hub", "lucide:folder", null);
        parent.setChildren(List.of(menu(2, "/my-hub/read-record", "lucide:book-open", "#123456"),
            menu(3, "/my-hub/weread", "svg:weread", "#654321")));
        var result = service.resolve(List.of(parent));
        assertEquals("2", result.cards().get("section.reading").menuId());
        assertEquals("3", result.cards().get("overview.read").menuId());
        assertEquals("svg:weread", result.cards().get("overview.read").icon());
        assertEquals("lucide:layout-grid", result.cards().get("section.links").icon());
        assertEquals(MenuVisualService.DEFAULT, result.cards().get("section.movie"));
    }

    @Test void 按用户角色与客户端读取授权树且非法颜色统一降级() {
        var user = new UserEntity();
        user.setRole("user, admin");
        when(users.selectById(7L)).thenReturn(user);
        when(menus.getAccessibleMenuTree(List.of("user", "admin"), MenuClient.MOBILE))
            .thenReturn(List.of(menu(9, "/time/timeTracker", "", "red")));
        var result = service.get(7L, MenuClient.MOBILE);
        assertEquals(MenuVisualService.DEFAULT.icon(), result.cards().get("section.time").icon());
        assertNull(result.cards().get("section.time").iconColor());
        verify(menus).getAccessibleMenuTree(List.of("user", "admin"), MenuClient.MOBILE);
    }
}
