package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.mapper.UserMenuHiddenMapper;
import top.aiolife.system.pojo.entity.UserMenuHiddenEntity;
import top.aiolife.system.pojo.vo.MenuRouteVO;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserMenuPreferenceServiceTest {
    private final IMenuService menuService = mock(IMenuService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserMenuHiddenMapper hidden = mock(UserMenuHiddenMapper.class);
    private final UserMenuPreferenceService service = new UserMenuPreferenceService(menuService, users, hidden);

    @BeforeEach
    void setup() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setRole("user");
        when(users.selectById(7L)).thenReturn(user);
        when(hidden.lockUser(7L)).thenReturn(7L);
        when(hidden.selectHiddenMenuIds(7L)).thenReturn(List.of());
        when(menuService.getAccessibleMenuTree(List.of("user"))).thenReturn(List.of(
                route(10, "/group", "BasicLayout", Map.of(),
                        route(11, "/one", "one/index", Map.of()),
                        route(12, "/two", "two/index", Map.of())),
                route(20, "/profile", "_core/profile/index", Map.of()),
                route(30, "/system-hidden", "hidden/index", Map.of("hideInMenu", true)),
                route(40, "/empty", "BasicLayout", Map.of())));
    }

    @Test
    void get_仅返回可配置菜单且隐藏项目仍可恢复() {
        when(hidden.selectHiddenMenuIds(7L)).thenReturn(List.of(11L, 999L));
        var result = service.get(7L);
        assertEquals(List.of("11"), result.hiddenMenuIds());
        assertEquals(1, result.menus().size());
        assertEquals(2, result.menus().getFirst().children().size());
        verify(hidden).selectHiddenMenuIds(7L);
    }

    @Test
    void save_拒绝无权限父分组与保护菜单且不修改原记录() {
        for (Long id : List.of(999L, 10L, 20L, 30L, 40L)) {
            assertThrows(IllegalArgumentException.class, () -> service.save(7L, List.of(id)));
        }
        verify(hidden, never()).physicalDeleteByUserId(anyLong());
        verify(hidden, never()).insert(any(UserMenuHiddenEntity.class));
    }

    @Test
    void save_去重并填写统一审计字段() {
        assertEquals(List.of("11"), service.save(7L, List.of(11L, 11L)).hiddenMenuIds());
        var entity = ArgumentCaptor.forClass(UserMenuHiddenEntity.class);
        var order = inOrder(hidden);
        order.verify(hidden).lockUser(7L);
        order.verify(hidden).selectHiddenMenuIds(7L);
        order.verify(hidden).physicalDeleteByUserId(7L);
        order.verify(hidden).insert(entity.capture());
        assertEquals(7L, entity.getValue().getUserId());
        assertEquals(11L, entity.getValue().getMenuId());
        assertEquals(0, entity.getValue().getIsDeleted());
        assertEquals(7L, entity.getValue().getCreateUser());
        assertNotNull(entity.getValue().getUpdateTime());
    }

    @Test
    void save_重复隐藏幂等恢复显示使用物理删除() {
        when(hidden.selectHiddenMenuIds(7L)).thenReturn(List.of(11L));
        service.save(7L, List.of(11L));
        verify(hidden, never()).physicalDeleteByUserId(anyLong());
        service.save(7L, List.of());
        verify(hidden).physicalDeleteByUserId(7L);
        verify(hidden, never()).insert(any(UserMenuHiddenEntity.class));
    }

    @Test
    void reset_仅删除当前用户包括已失效菜单的记录() {
        assertTrue(service.reset(7L).hiddenMenuIds().isEmpty());
        verify(hidden).physicalDeleteByUserId(7L);
        verify(hidden, never()).insert(any(UserMenuHiddenEntity.class));
    }

    @Test
    void save_用户不存在或请求缺失时不删除数据() {
        assertThrows(IllegalArgumentException.class, () -> service.save(8L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.save(7L, null));
        verify(hidden, never()).physicalDeleteByUserId(anyLong());
    }

    @Test
    void get_雪花ID不丢精度且单入口分组不暴露隐藏子路由() {
        long id = 9007199254740993L;
        when(menuService.getAccessibleMenuTree(List.of("user"))).thenReturn(List.of(
                route(id, "/single", "BasicLayout", Map.of("hideChildrenInMenu", true),
                        route(51, "/single/detail", "detail/index", Map.of()))));
        when(hidden.selectHiddenMenuIds(7L)).thenReturn(List.of(id));
        var result = service.get(7L);
        assertEquals("9007199254740993", result.menus().getFirst().id());
        assertTrue(result.menus().getFirst().children().isEmpty());
        assertEquals(List.of("9007199254740993"), result.hiddenMenuIds());
    }

    @Test
    void mapper_物理删除语句不被TableLogic改写且包含用户条件() {
        MybatisConfiguration config = new MybatisConfiguration();
        config.addMapper(UserMenuHiddenMapper.class);
        var statement = config.getMappedStatement(UserMenuHiddenMapper.class.getName() + ".physicalDeleteByUserId");
        assertEquals(SqlCommandType.DELETE, statement.getSqlCommandType());
        var sql = statement.getBoundSql(Map.of("userId", 7L));
        assertEquals("DELETE FROM user_menu_hidden WHERE user_id = ?", sql.getSql());
        assertEquals("userId", sql.getParameterMappings().getFirst().getProperty());
    }

    private MenuRouteVO route(long id, String path, String component, Map<String, Object> meta, MenuRouteVO... children) {
        MenuRouteVO result = new MenuRouteVO();
        result.setId(id);
        result.setName(path);
        result.setPath(path);
        result.setComponent(component);
        result.setMeta(meta);
        result.setChildren(List.of(children));
        return result;
    }
}
