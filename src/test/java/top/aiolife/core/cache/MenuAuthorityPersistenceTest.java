package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.system.mapper.ISysMenuMapper;
import static org.junit.jupiter.api.Assertions.*;

class MenuAuthorityPersistenceTest {
    @Test void 同一SqlSession中的配置查询不会复用旧结果() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:menus_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        String audit = ", create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP, is_deleted INT DEFAULT 0";
        jdbc.execute("CREATE TABLE user_secondary_lock_menu(id BIGINT PRIMARY KEY, user_id BIGINT, menu_id BIGINT" + audit + ")");
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY, parent_id BIGINT, name VARCHAR(30), path VARCHAR(100), icon_color VARCHAR(20), component VARCHAR(100), redirect VARCHAR(100), meta VARCHAR(100), roles VARCHAR(30), sort INT, status INT" + audit + ")");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserSecondaryLockMenuMapper.class); config.addMapper(ISysMenuMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        try (var session = factory.getObject().openSession(true)) {
            var reader = new MenuDataCache(session.getMapper(UserSecondaryLockMenuMapper.class), session.getMapper(ISysMenuMapper.class));
            assertTrue(reader.getLockedMenuIds(7).isEmpty());
            assertTrue(reader.getEnabledMenus().isEmpty());
            // 独立连接模拟另一实例提交，当前SqlSession保持不变且不手动clearCache。
            jdbc.update("INSERT INTO user_secondary_lock_menu(id,user_id,menu_id) VALUES(1,7,9)");
            jdbc.update("INSERT INTO sys_menu(id,path,status,sort) VALUES(9,'/secret',1,0)");
            assertEquals(java.util.List.of(9L), reader.getLockedMenuIds(7));
            assertEquals("/secret", reader.getEnabledMenus().getFirst().getPath());
        } finally { jdbc.execute("DROP ALL OBJECTS"); }
    }
}
