package top.aiolife.record.api;

import top.aiolife.support.CurrentSchema;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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

/** H2 + 生产表结构及分页插件：同排序值跨页不能遗漏/重复记录。 */
class PaginationOrderingSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:pagination_order_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        String schema = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        for (String table : List.of("task", "task_column", "memo", "sys_dict_type", "sys_dict_data")) {
            jdbc.execute(CurrentSchema.table(table)
                    .replaceAll(" COLLATE [a-zA-Z0-9_]+", "")
                    .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+", "")
                    .replace("KEY `idx_", "KEY `" + table + "_idx_"));
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new MybatisPlusConfig().mybatisPlusInterceptor());
        for (Class<?> mapper : List.of(ITaskMapper.class, ITaskColumnMapper.class, IMemoMapper.class,
                ISysDictDataMapper.class, ISysDictTypeMapper.class)) configuration.addMapper(mapper);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
        for (int id : List.of(5, 3, 1, 4, 2)) {
            jdbc.update("INSERT INTO task(id,user_id,content,column_id,sort_order) VALUES (?,7,'任务',?,0)", id, id % 2);
            jdbc.update("INSERT INTO task_column(id,user_id,title,sort_order) VALUES (?,7,'任务栏',0)", id);
            jdbc.update("INSERT INTO memo(id,user_id,title,update_time) VALUES (?,7,'笔记','2026-10-22 10:00:00')", id);
            jdbc.update("INSERT INTO sys_dict_type(dict_id,dict_name,dict_type) VALUES (?,'分类',?)", id, "type-" + id);
            jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_id,sort_order,dict_label) VALUES (?,1,0,'字典')", id);
        }
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new QueryParamsArgumentResolver(json))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    private void assertPages(Object controller, String path, String idField, List<String> expected) throws Exception {
        MockMvc mvc = mvc(controller);
        List<String> ids = new ArrayList<>();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
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
        var tasks = new TaskController(mock(ITaskService.class), session.getMapper(ITaskMapper.class), mock(ITaskDetail.class));
        var columns = new TaskColumnController(session.getMapper(ITaskColumnMapper.class), mock(ITaskColumnService.class));
        assertPages(tasks, "/tasks", "id", List.of("1", "2", "3", "4", "5"));
        assertPages(columns, "/taskColumn/query", "id", List.of("1", "2", "3", "4", "5"));
    }

    @Test
    void testMemo_同更新时间按ID倒序跨页() throws Exception {
        assertPages(new MemoController(mock(IMemoService.class), session.getMapper(IMemoMapper.class)),
                "/memo/query", "id", List.of("5", "4", "3", "2", "1"));
    }

    @Test
    void testDictionary_同排序值的字典与类型具有唯一排序() throws Exception {
        var guard = mock(BankCardDictionaryGuard.class);
        var data = session.getMapper(ISysDictDataMapper.class);
        var types = session.getMapper(ISysDictTypeMapper.class);
        assertPages(new SysDictDataController(guard, data, types), "/sysDictData/query", "dictCode", List.of("1", "2", "3", "4", "5"));
        assertPages(new SysDictTypeController(guard, types, data), "/sysDictType/query", "dictId", List.of("1", "2", "3", "4", "5"));
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
        var controller = new UserDictDataController(mock(UserDictDataMapper.class), new UserDictDataServiceImpl(cache));
        assertPages(controller, "/userDictData/query", "id", List.of("1", "2", "3", "4", "5"));
    }

    @Test
    void testUserDictionary_合法超大页码返回空页而不溢出() throws Exception {
        var service = mock(UserDictDataService.class);
        UserDictDataEntity item = new UserDictDataEntity();
        item.setId(1L);
        when(service.listUserVisibleDictData(7L, null, true)).thenReturn(List.of(item));
        var controller = new UserDictDataController(mock(UserDictDataMapper.class), service);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc(controller).perform(get("/userDictData/query").param("page", "2147483647").param("pageSize", "200"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
        }
    }
}
