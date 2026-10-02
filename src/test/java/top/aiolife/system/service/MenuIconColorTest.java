package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.core.cache.MenuDataCache;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.mapper.IUserQuickNavMapper;
import top.aiolife.system.pojo.entity.UserQuickNavEntity;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.service.impl.MenuServiceImpl;
import top.aiolife.system.service.impl.QuickNavServiceImpl;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuIconColorTest {
    @Test
    void iconColor_独立持久化并支持修改清空及各入口回显() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:menu_color;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE sys_menu (
                    id BIGINT PRIMARY KEY, parent_id BIGINT, name VARCHAR(64), path VARCHAR(255),
                    component VARCHAR(255), redirect VARCHAR(255), icon_color VARCHAR(7), meta VARCHAR(2000),
                    roles VARCHAR(255), sort INT, status INT, is_deleted INT,
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        var config = new MybatisConfiguration();
        config.addMapper(ISysMenuMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        try (var session = factory.getObject().openSession(true)) {
            var mapper = session.getMapper(ISysMenuMapper.class);
            var cache = mock(MenuDataCache.class);
            when(cache.getEnabledMenus()).thenAnswer(call -> mapper.selectList(null));
            var objectMapper = new ObjectMapper();
            var menus = new MenuServiceImpl(mapper, objectMapper, cache);
            var req = new MenuSaveReq();
            req.setName("Sample"); req.setPath("/sample"); req.setComponent("sample/index");
            req.setMeta(Map.of("title", "示例", "icon", "lucide:book", "keepAlive", true));
            req.setIconColor(" #427bea ");
            var created = menus.create(req, 1L);
            long id = created.getId();
            assertEquals("#427bea", mapper.selectById(id).getIconColor());
            assertEquals("#427bea", menus.getAdminMenuTree().getFirst().getIconColor());
            var route = menus.getAccessibleMenuTree(List.of()).getFirst();
            assertEquals("#427bea", route.getIconColor());
            assertFalse(route.getMeta().containsKey("iconColor"));
            assertEquals(true, route.getMeta().get("keepAlive"));

            var quickMapper = mock(IUserQuickNavMapper.class);
            var item = new UserQuickNavEntity(); item.setMenuId(id); item.setEnabled(1); item.setSortOrder(0);
            when(quickMapper.selectList(any())).thenReturn(List.of(item));
            var quick = new QuickNavServiceImpl(quickMapper, mapper, menus, objectMapper);
            assertEquals("#427bea", quick.listCandidates(List.of()).getFirst().getColor());
            assertEquals("#427bea", quick.listMy(1L).getFirst().getColor());

            req.setIconColor("#abcdef");
            menus.update(id, req, 1L);
            assertEquals("#abcdef", mapper.selectById(id).getIconColor());
            req.setIconColor("");
            assertNull(menus.update(id, req, 1L).getIconColor());
            assertNull(mapper.selectById(id).getIconColor());
            assertNull(quick.listMy(1L).getFirst().getColor());
            req.setIconColor("red");
            assertThrows(IllegalArgumentException.class, () -> menus.update(id, req, 1L));
            assertNull(mapper.selectById(id).getIconColor());
            req.setIconColor(null);
            assertNull(menus.update(id, req, 1L).getIconColor());
        }
    }
}
