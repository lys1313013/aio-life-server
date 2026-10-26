package top.aiolife.system.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.system.mapper.ActivityLogMapper;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.query.ActivityLogQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 默认独立 H2；可指定专用临时 MySQL 库，验证相同生产 Mapper。 */
class ActivityLogPersistenceTest {
    private ActivityLogService service;
    private ActivityLogMapper mapper;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach void setup() throws Exception {
        String mysql = System.getenv("AIO_LOG_TEST_MYSQL_URL");
        var ds = new DriverManagerDataSource();
        if (mysql == null) {
            ds.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa"); ds.setPassword("");
        } else {
            assertTrue(mysql.matches("jdbc:mysql://127\\.0\\.0\\.1:13317/aio_operation_test_[a-z0-9]+(?:\\?.*)?"), "只允许专用本地测试库");
            ds.setUrl(mysql); ds.setUsername("root"); ds.setPassword("");
        }
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE IF EXISTS sys_activity_log");
        String full = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        int start = full.indexOf("CREATE TABLE IF NOT EXISTS `sys_activity_log`");
        String schema = full.substring(start, full.indexOf(";", start));
        if (mysql == null) schema = schema.substring(0, schema.indexOf(" ENGINE="));
        jdbc.execute(schema);
        var config = new MybatisConfiguration(); config.addMapper(ActivityLogMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/ActivityLogMapper.xml"));
        var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ActivityLogMapper.class);
        service = new ActivityLogService(mapper);
        insert(1, "OPERATION", null, "2026-10-20T23:59:59", 0, 0);
        insert(2, "OPERATION", "2026-10-21T00:00:00", "2026-10-20T23:59:59", 0, 0);
        insert(3, "OPERATION", "2026-10-21T23:59:59.999", "2026-10-21T23:59:59", 15, 0);
        insert(4, "OPERATION", "2026-10-22T00:00:00", "2026-10-21T23:59:59", null, 0);
        insert(5, "ACCESS", null, "2026-10-21T10:00:00", null, 0);
        insert(6, "OPERATION", null, "2026-10-21T11:00:00", 1, 1);
        insert(7, "OPERATION", null, "2026-10-21T12:00:00", null, 0);
    }

    private void insert(long id, String type, String requestTime, String createTime, Integer ms, int deleted) {
        var row = new ActivityLogEntity(); row.fillCreateCommonField(13L);
        row.setId(id); row.setLogType(type); row.setUsername("a_%"); row.setUserId(9223372036854775806L);
        row.setRequestMethod("POST"); row.setRequestPath("/tasks/{id}"); row.setFunctionName("待办"); row.setFunctionItem("修改");
        row.setIpAddress("2001:db8::2"); row.setServerIp("2001:db8::1"); row.setSuccess(id % 2 == 0);
        row.setRequestTime(requestTime == null ? null : LocalDateTime.parse(requestTime));
        row.setCreateTime(LocalDateTime.parse(createTime)); row.setDurationMs(ms); row.setIsDeleted(deleted);
        mapper.insert(row);
    }

    private ActivityLogQuery day() {
        var query = new ActivityLogQuery(); query.setStartDate(LocalDate.of(2026,10,21)); query.setEndDate(LocalDate.of(2026,10,21));
        return query;
    }

    @Test void 请求时间过滤正确处理跨日及毫秒且不使用审计时间回退() {
        var result = service.list("OPERATION", day());
        assertEquals(List.of(3L,2L), result.getItems().stream().map(ActivityLogEntity::getId).toList());
        assertEquals(2L,result.getTotal());
        assertEquals(999000000,result.getItems().getFirst().getRequestTime().getNano());
        var query = day(); query.setPageSize(1); query.setPage(2);
        assertEquals(2L,service.list("OPERATION",query).getItems().getFirst().getId());
        assertEquals(2L,service.list("OPERATION",query).getTotal());
    }

    @Test void 账号筛选按通配符字面值匹配() {
        var query = day(); query.setUsername("a_%");
        assertEquals(2L, service.list("OPERATION",query).getTotal());
        query.setUsername("aX"); assertEquals(0L, service.list("OPERATION",query).getTotal());
    }

    @Test void 详情拒绝其他类型和软删除并且列表不返回参数() {
        assertEquals(2L, service.detail(2L).getId());
        assertThrows(IllegalArgumentException.class, () -> service.detail(5L));
        assertThrows(IllegalArgumentException.class, () -> service.detail(6L));
        assertThrows(IllegalArgumentException.class, () -> service.detail(100L));
        String value = "{\"path\":{\"id\":\"9223372036854775806\"}}";
        if (System.getenv("AIO_LOG_TEST_MYSQL_URL") == null) jdbc.update("UPDATE sys_activity_log SET request_params=? FORMAT JSON WHERE id=2",value);
        else jdbc.update("UPDATE sys_activity_log SET request_params=? WHERE id=2",value);
        assertTrue(service.detail(2L).getRequestParams().contains("9223372036854775806"));
        assertTrue(service.list("OPERATION",day()).getItems().stream().allMatch(row -> row.getRequestParams()==null));
        var query = day(); assertEquals(3L,mapper.operationMaxId(query));
        assertEquals(List.of(3L,2L),mapper.exportOperations(query,3L,null).stream().map(ActivityLogEntity::getId).toList());
        assertEquals(List.of(2L),mapper.exportOperations(query,3L,3L).stream().map(ActivityLogEntity::getId).toList());
    }
    @Test void 业务事务回滚后独立日志事务仍保留() {
        var transactions = new DataSourceTransactionManager(dataSource);
        var proxy = new ProxyFactory(new ActivityLogRecorder(mapper, mock(UserMapper.class), mock(ISysMenuMapper.class), new ObjectMapper()));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var recorder = (ActivityLogRecorder) proxy.getProxy();
        var log = new ActivityLogEntity(); log.fillCreateCommonField(13L); log.setId(100L);
        log.setLogType("OPERATION"); log.setSuccess(false); log.setRequestPath("/tasks");
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            insert(99L,"OPERATION",null,"2026-10-21T12:00:00",1,0);
            recorder.record(log,null,null);
            throw new IllegalStateException("business rollback");
        }));
        assertNull(mapper.selectById(99L));
        assertFalse(service.detail(100L).getSuccess());
    }

}
