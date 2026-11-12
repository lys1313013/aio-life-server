package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.sso.service.SecondaryLockMenuResolver;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.mapper.UserMenuHiddenMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.service.impl.MenuServiceImpl;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuClientStatusTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private MenuServiceImpl service;
    private ISysMenuMapper mapper;
    private SecondaryLockMenuResolver locks;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:menu_client_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_menu (
                    id BIGINT PRIMARY KEY, parent_id BIGINT, name VARCHAR(64), path VARCHAR(255),
                    component VARCHAR(255), redirect VARCHAR(255), icon_color VARCHAR(7), meta VARCHAR(2000),
                    roles VARCHAR(255), sort_order INT, visible_clients INT NOT NULL DEFAULT 3, is_deleted INT,
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ISysMenuMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source); factory.setConfiguration(config);
        session = factory.getObject().openSession(true);
        mapper = session.getMapper(ISysMenuMapper.class);
        var lockMapper = mock(UserSecondaryLockMenuMapper.class);
        var lock = new UserSecondaryLockMenuEntity(); lock.setMenuId(1L);
        when(lockMapper.selectForAccessControl(7L)).thenReturn(List.of(lock));
        var reader = new MenuAccessReader(mapper);
        service = new MenuServiceImpl(mapper, new ObjectMapper(), reader);
        locks = new SecondaryLockMenuResolver(lockMapper, reader);
        jdbc.update("""
                INSERT INTO sys_menu(id,parent_id,name,path,component,sort_order,visible_clients,is_deleted) VALUES
                (1,0,'Group','/group','BasicLayout',0,2,0),
                (2,1,'Child','/group/child','sample/index',0,3,0),
                (3,0,'Web','/web','sample/index',0,1,0),
                (4,0,'Neither','/neither','sample/index',0,0,0),
                (5,0,'Removed','/removed','sample/index',0,3,1)
                """);
    }

    @AfterEach void close() { session.close(); jdbc.execute("DROP ALL OBJECTS"); }

    @Test void 分端过滤父级角色及删除状态并用于快捷菜单() {
        assertEquals(List.of("/web"), service.getAccessibleMenuTree(List.of("user")).stream().map(m -> m.getPath()).toList());
        var mobile = service.getAccessibleMenuTree(List.of("user"), MenuClient.MOBILE);
        assertEquals("/group", mobile.getFirst().getPath());
        assertEquals("/group/child", mobile.getFirst().getChildren().getFirst().getPath());
        assertEquals(List.of(2L), service.listAccessibleLeaves(List.of("user"), MenuClient.MOBILE).stream().map(m -> m.get("menuId")).toList());
        jdbc.update("UPDATE sys_menu SET roles='admin' WHERE id=1");
        assertTrue(service.getAccessibleMenuIds(List.of("user"), MenuClient.MOBILE).isEmpty());
        assertEquals(2, service.getAccessibleMenuIds(List.of("admin"), MenuClient.MOBILE).size());
        assertEquals(4, service.getAdminMenuTree().stream().mapToInt(m -> 1 + (m.getChildren() == null ? 0 : m.getChildren().size())).sum());
    }

    @Test void 单字段更新保留菜单其他属性且任何展示组合都不能绕过二级锁() {
        for (int mask : List.of(0, 1, 2, 3)) {
            var saved = service.updateVisibleClients(1L, mask, 7L);
            assertEquals(mask, saved.getVisibleClients());
            assertEquals("Group", saved.getName());
            assertEquals(7L, mapper.selectById(1L).getUpdateUser());
            assertEquals(java.util.Set.of("/group"), locks.findMatchedPaths(7L, "/group/child"));
            for (MenuClient client : MenuClient.values()) {
                boolean visible = client.isVisible(mask);
                assertEquals(visible, service.getAccessibleMenuIds(List.of("user"), client).contains(2L));
                assertEquals(visible, service.listAccessibleLeaves(List.of("user"), client).stream()
                        .anyMatch(menu -> menu.get("menuId").equals(2L)));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibleClients(1L, 4, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibleClients(999L, 1, 7L));
    }

    @Test void 新增默认双端展示且缺省更新保留原位掩码() throws Exception {
        var req = new MenuSaveReq(); req.setName("New"); req.setPath("/new");
        var created = service.create(req, 7L);
        assertEquals(3, created.getVisibleClients());
        service.updateVisibleClients(created.getId(), 0, 7L);
        assertEquals(0, service.update(created.getId(), req, 7L).getVisibleClients());
        for (int mask : List.of(0, 1, 2, 3)) {
            req.setVisibleClients(mask);
            assertEquals(mask, service.update(created.getId(), req, 7L).getVisibleClients());
        }
        for (int invalid : List.of(-1, 4, 5)) {
            req.setVisibleClients(invalid);
            assertThrows(IllegalArgumentException.class, () -> service.update(created.getId(), req, 7L));
        }
        assertEquals(MenuClient.MOBILE, MenuClient.parse("mobile"));
        assertThrows(IllegalArgumentException.class, () -> MenuClient.parse("unknown"));
    }

    @Test void 隐藏父级后个人目录不保留空分组() {
        var users = mock(UserMapper.class);
        var user = new UserEntity(); user.setId(7L); user.setRole("user");
        when(users.selectById(7L)).thenReturn(user);
        var hidden = mock(UserMenuHiddenMapper.class);
        when(hidden.selectHiddenMenuIds(7L)).thenReturn(List.of());
        var preferences = new UserMenuPreferenceService(service, users, hidden);
        service.updateVisibleClients(1L, 1, 7L);
        assertTrue(preferences.get(7L, MenuClient.MOBILE).menus().isEmpty());
        assertFalse(preferences.get(7L, MenuClient.WEB).menus().isEmpty());
    }
}
