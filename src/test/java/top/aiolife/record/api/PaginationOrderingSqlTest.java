package top.aiolife.record.api;

import top.aiolife.support.CurrentSchema;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import top.aiolife.bankcard.service.BankCardDictionaryGuard;
import top.aiolife.config.JsonConfig;
import top.aiolife.config.MybatisPlusConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.core.query.QueryParamsArgumentResolver;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.mapper.ISysDictDataMapper;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.mapper.ITaskColumnMapper;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.IMemoService;
import top.aiolife.record.service.ITaskColumnService;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.record.service.UserDictDataCache;
import top.aiolife.record.service.impl.UserDictDataServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 自建 PostgreSQL 18、生产表结构及分页插件：同排序值跨页不能遗漏/重复记录。 */
@Tag("integration")
class PaginationOrderingSqlTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18-alpine")
            .withEnv("POSTGRES_PASSWORD", "isolated-pagination")
            .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofMinutes(2));
    private static JdbcTemplate jdbc;
    private static SqlSession session;
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());

    @BeforeAll
    static void 启动数据库() throws Exception {
        POSTGRES.start();
        var source = new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/fixture?stringtype=unspecified");
        source.setUser("postgres");
        source.setPassword("isolated-pagination");
        jdbc = new JdbcTemplate(source);
        for (String table : List.of("task", "task_column", "memo", "sys_dict_type", "sys_dict_data")) {
            jdbc.execute(CurrentSchema.table(table));
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new MybatisPlusConfig().mybatisPlusInterceptor());
        for (Class<?> mapper : List.of(ITaskMapper.class, ITaskColumnMapper.class, IMemoMapper.class,
                ISysDictDataMapper.class, ISysDictTypeMapper.class)) configuration.addMapper(mapper);
        // 自动提交，避免查询事务的共享锁阻塞清空夹具的 TRUNCATE。
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
    }

    @AfterAll
    static void 停止数据库() {
        if (session != null) session.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void 插入夹具() {
        for (int id : List.of(5, 3, 1, 4, 2)) {
            jdbc.update("INSERT INTO task(id,user_id,content,column_id,sort_order) VALUES (?,7,'任务',?,0)", id, id % 2);
            jdbc.update("INSERT INTO task_column(id,user_id,title,sort_order) VALUES (?,7,'任务栏',0)", id);
            jdbc.update("INSERT INTO memo(id,user_id,title,update_time) VALUES (?,7,'笔记','2026-10-22 10:00:00')", id);
            jdbc.update("INSERT INTO sys_dict_type(dict_id,dict_name,dict_type) VALUES (?,'分类',?)", id, "type-" + id);
            jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_id,sort_order,dict_label) VALUES (?,1,0,'字典')", id);
        }
    }

    @AfterEach
    void 清空夹具() {
        jdbc.execute("TRUNCATE task, task_column, memo, sys_dict_type, sys_dict_data");
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new QueryParamsArgumentResolver(json),
                        new top.aiolife.core.web.CurrentUserIdArgumentResolver(),
                        new top.aiolife.core.web.CurrentTokenArgumentResolver())
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private void assertPages(Object controller, String path, String idField, List<String> expected) throws Exception {
        MockMvc mvc = mvc(controller);
        List<String> ids = new ArrayList<>();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            login.when(StpUtil::getLoginIdAsString).thenReturn("7");
            for (int page = 1; page <= 4; page++) {
                JsonNode response = json.readTree(mvc.perform(get(path).param("page", Integer.toString(page))
                                .param("pageSize", "2")).andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data.total").value(5)).andReturn().getResponse().getContentAsByteArray());
                JsonNode items = response.path("data").path("items");
                assertEquals(page <= 2 ? 2 : page == 3 ? 1 : 0, items.size());
                for (JsonNode item : items) ids.add(item.path(idField).asText());
            }
        }
        assertEquals(expected, ids);
    }

    @Test
    void testTaskAndColumns_同排序值不同列也按ID稳定跨页() throws Exception {
        var detail = mock(ITaskDetail.class);
        when(detail.getUnCompletedCount(any(), anyLong())).thenReturn(java.util.Map.of());
        var taskService = new top.aiolife.record.service.impl.TaskServiceImpl(detail);
        org.springframework.test.util.ReflectionTestUtils.setField(taskService, "baseMapper", session.getMapper(ITaskMapper.class));
        var tasks = new TaskController(taskService);
        var columnService = new top.aiolife.record.service.impl.TaskColumnServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(columnService, "baseMapper", session.getMapper(ITaskColumnMapper.class));
        var columns = new TaskColumnController(columnService);
        assertPages(tasks, "/tasks", "id", List.of("1", "2", "3", "4", "5"));
        assertPages(columns, "/taskColumn/query", "id", List.of("1", "2", "3", "4", "5"));
    }

    @Test
    void testMemo_同更新时间按ID倒序跨页() throws Exception {
        var memoService = new top.aiolife.record.service.impl.MemoServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(memoService, "baseMapper", session.getMapper(IMemoMapper.class));
        assertPages(new MemoController(memoService),
                "/memo/query", "id", List.of("5", "4", "3", "2", "1"));
    }

    @Test
    void testDictionary_同排序值的字典与类型具有唯一排序() throws Exception {
        var guard = mock(BankCardDictionaryGuard.class);
        var data = session.getMapper(ISysDictDataMapper.class);
        var types = session.getMapper(ISysDictTypeMapper.class);
        var dictService = new top.aiolife.record.service.impl.SysDictServiceImpl(data, types, guard);
        assertPages(new SysDictDataAdminController(dictService), "/sysDictData/query", "dictCode", List.of("1", "2", "3", "4", "5"));
        assertPages(new SysDictTypeAdminController(dictService), "/sysDictType/query", "dictId", List.of("1", "2", "3", "4", "5"));
    }

    @Test
    void testUserDictionary_公共和个人分类同排序值时按ID稳定分页() throws Exception {
        var cache = mock(UserDictDataCache.class);
        List<UserDictDataEntity> entries = new ArrayList<>();
        for (long id : List.of(5L, 3L, 1L, 4L, 2L)) {
            UserDictDataEntity entry = new UserDictDataEntity();
            entry.setId(id);
            entry.setSortOrder(0);
            entry.setIsEnabled(1);
            entries.add(entry);
        }
        when(cache.list(0L, null)).thenReturn(entries.subList(0, 3));
        when(cache.list(7L, null)).thenReturn(entries.subList(3, 5));
        var controller = new UserDictDataController(new UserDictDataServiceImpl(cache));
        assertPages(controller, "/userDictData/query", "id", List.of("1", "2", "3", "4", "5"));
    }

    @Test
    void testUserDictionary_合法超大页码返回空页而不溢出() throws Exception {
        var cache = mock(UserDictDataCache.class);
        UserDictDataEntity item = new UserDictDataEntity();
        item.setId(1L);
        when(cache.list(0L, null)).thenReturn(List.of());
        when(cache.list(7L, null)).thenReturn(List.of(item));
        var controller = new UserDictDataController(new UserDictDataServiceImpl(cache));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            login.when(StpUtil::getLoginIdAsString).thenReturn("7");
            mvc(controller).perform(get("/userDictData/query").param("page", "2147483647").param("pageSize", "200"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
        }
    }
}
