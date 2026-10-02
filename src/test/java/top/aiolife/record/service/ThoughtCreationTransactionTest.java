package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.ThoughtSaveEventReq;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Spring 事务代理和 Mapper SQL；独立 H2，不连接用户数据库。 */
class ThoughtCreationTransactionTest {
    JdbcTemplate jdbc;
    ThoughtCreationService service;
    ValidatorFactory validation;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:thought_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        String audit = ", create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP, is_deleted INT DEFAULT 0";
        jdbc.execute("CREATE TABLE thought(id BIGINT PRIMARY KEY, user_id BIGINT, content VARCHAR(1000) NOT NULL, is_pinned INT, hidden_content INT" + audit + ")");
        // 在第二条事件制造数据库失败，验证已插入的主体和第一条事件都被回滚。
        jdbc.execute("CREATE TABLE thought_rela_event(id BIGINT PRIMARY KEY, thought_id BIGINT, content VARCHAR(1000) NOT NULL CHECK(content <> 'FAIL')" + audit + ")");
        var cfg = new MybatisConfiguration(); cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(IThoughtMapper.class); cfg.addMapper(IRelaEventMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(cfg);
        var session = new SqlSessionTemplate(factory.getObject());
        validation = Validation.buildDefaultValidatorFactory();
        var target = new ThoughtCreationService(session.getMapper(IThoughtMapper.class), session.getMapper(IRelaEventMapper.class), validation.getValidator());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (ThoughtCreationService) proxy.getProxy();
    }

    @AfterEach void close() { validation.close(); jdbc.execute("DROP ALL OBJECTS"); }

    ThoughtSaveReq request(String... contents) {
        var req = new ThoughtSaveReq(); req.setContent("正文");
        req.setEvents(Arrays.stream(contents).map(content -> {var event = new ThoughtSaveEventReq(); event.setContent(content); return event;}).toList());
        return req;
    }

    @Test void 第二条事件数据库失败时主从记录全部回滚() {
        assertThrows(RuntimeException.class, () -> service.create(7, request("first", "FAIL")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM thought", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM thought_rela_event", Integer.class));
    }

    @Test void 成功创建主从记录且补全审计字段() {
        service.create(7, request("first", "second"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM thought WHERE user_id=7 AND create_user=7", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM thought_rela_event WHERE create_user=7", Integer.class));
    }

    @Test void 直接服务调用也校验嵌套空事件及正文() {
        var blank = request("  ");
        var missing = request(); missing.setEvents(Arrays.asList((ThoughtSaveEventReq)null));
        var noContent = request("event"); noContent.setContent(null);
        for (var req : List.of(blank, missing, noContent))
            assertThrows(IllegalArgumentException.class, () -> service.create(7, req));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM thought", Integer.class));
    }
}
