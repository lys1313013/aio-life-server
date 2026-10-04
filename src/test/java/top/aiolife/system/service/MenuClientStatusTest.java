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
import top.aiolife.core.cache.MenuDataCache;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
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
    private SecondaryLockMenuCache locks;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:menu_client_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_menu (
                    id BIGINT PRIMARY KEY, parent_id BIGINT, name VARCHAR(64), path VARCHAR(255),
                    component VARCHAR(255), redirect VARCHAR(255), icon_color VARCHAR(7), meta VARCHAR(2000),
                    roles VARCHAR(255), sort INT, status INT, mobile_status INT, is_deleted INT,
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
        var cache = new MenuDataCache(lockMapper, mapper);
        service = new MenuServiceImpl(mapper, new ObjectMapper(), cache);
        locks = new SecondaryLockMenuCache(cache);
        jdbc.update("""
                INSERT INTO sys_menu(id,parent_id,name,path,component,sort,status,mobile_status,is_deleted) VALUES
                (1,0,'Group','/group','BasicLayout',0,0,1,0),
                (2,1,'Child','/group/child','sample/index',0,1,1,0),
                (3,0,'Web','/web','sample/index',0,1,0,0),
                (4,0,'Neither','/neither','sample/index',0,0,0,0),
                (5,0,'Removed','/removed','sample/index',0,1,1,1)
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

    @Test void 单独更新不覆盖另一端或其他菜单属性且锁始终有效() throws Exception {
        assertEquals(java.util.Set.of("/group"), locks.findMatchedPaths(7L, "/group/child"));
        var web = service.updateStatus(1L, 1, 7L);
        assertEquals(1, web.getMobileStatus());
        var mobile = service.updateMobileStatus(1L, 0, 7L);
        assertEquals(1, mobile.getStatus());
        assertEquals(0, mobile.getMobileStatus());
        assertEquals("Group", mobile.getName());
        assertEquals(7L, mapper.selectById(1L).getUpdateUser());
        service.updateStatus(1L, 0, 7L);
        assertEquals(java.util.Set.of("/group"), locks.findMatchedPaths(7L, "/group/child"));
        assertThrows(IllegalArgumentException.class, () -> service.updateMobileStatus(1L, 2, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.updateMobileStatus(999L, 1, 7L));
    }

    @Test void 新增复制Web状态但旧客户端编辑保留移动状态() throws Exception {
        var req = new MenuSaveReq(); req.setName("New"); req.setPath("/new"); req.setStatus(0);
        var created = service.create(req, 7L);
        assertEquals(0, created.getMobileStatus());
        service.updateMobileStatus(created.getId(), 1, 7L);
        assertEquals(1, service.update(created.getId(), req, 7L).getMobileStatus());
        req.setMobileStatus(0);
        assertEquals(0, service.update(created.getId(), req, 7L).getMobileStatus());
        req.setMobileStatus(9);
        assertThrows(IllegalArgumentException.class, () -> service.update(created.getId(), req, 7L));
        assertEquals(MenuClient.MOBILE, MenuClient.parse("mobile"));
        assertThrows(IllegalArgumentException.class, () -> MenuClient.parse("unknown"));
    }
}
