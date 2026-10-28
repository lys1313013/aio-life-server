package top.aiolife.wardrobe.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.support.CurrentSchema;
import top.aiolife.wardrobe.api.WardrobeController;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.dto.WardrobeStatsAggregate;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H2 MySQL 模式执行生产聚合 SQL，验证统计口径、数据隔离与响应数字类型。 */
class WardrobeStatsSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private WardrobeItemServiceImpl service;
    private WardrobeCategoryMapper categories;
    private long nextItemId;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:wardrobe_stats_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        for (String table : List.of("wardrobe_category", "wardrobe_item")) {
            jdbc.execute(CurrentSchema.h2Table(table)
                    .replaceAll(" COLLATE [a-zA-Z0-9_]+", "")
                    .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+", ""));
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(WardrobeItemMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
        categories = mock(WardrobeCategoryMapper.class);
        service = new WardrobeItemServiceImpl(categories);
        ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(WardrobeItemMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void getStats_空衣柜保留四季零值和空分类() {
        var stats = service.getStats(7L);

        assertEquals(0, stats.getTotalCount());
        assertTrue(stats.getCategoryCount().isEmpty());
        assertEquals(Map.of("春", 0, "夏", 0, "秋", 0, "冬", 0), stats.getSeasonCount());
        assertEquals(0, stats.getTotalValue().signum());
        assertEquals(0, stats.getAvgPrice().signum());
        verifyNoInteractions(categories);
    }

    @Test
    void getStats_同名分类合并且失效和越权分类归未分类() {
        category(11, "上装", 0, null, 0);
        category(12, "上装", 1, 7L, 0);
        category(13, "他人分类", 1, 8L, 0);
        category(14, "已删除分类", 0, null, 1);
        category(15, "A", 0, null, 0);
        category(16, "a", 0, null, 0);
        category(17, "A ", 0, null, 0);
        category(18, "未分类", 0, null, 0);
        for (long categoryId : List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 99L)) {
            item(7L, categoryId, null, null, 0);
        }
        item(7L, null, null, null, 0);

        var stats = service.getStats(7L);

        assertEquals(10, stats.getTotalCount());
        assertEquals(Map.of("上装", 2, "未分类", 5, "A", 1, "a", 1, "A ", 1), stats.getCategoryCount());
        assertEquals(stats.getTotalCount(), stats.getCategoryCount().values().stream().mapToInt(Integer::intValue).sum());
        verifyNoInteractions(categories);
    }

    @Test
    void getStats_季节每件最多一次且空价格计入均价分母() {
        item(7L, null, "春,春,夏", "2.00", 0);
        item(7L, null, "秋,冬", null, 0);
        item(7L, null, null, null, 0);

        var stats = service.getStats(7L);

        assertEquals(3, stats.getTotalCount());
        assertEquals(Map.of("春", 1, "夏", 1, "秋", 1, "冬", 1), stats.getSeasonCount());
        assertEquals(new BigDecimal("2.00"), stats.getTotalValue());
        assertEquals(new BigDecimal("0.67"), stats.getAvgPrice());
    }

    @Test
    void getStats_过滤其他用户及删除衣物且HTTP计数仍为数字() throws Exception {
        item(7L, null, "春", "10.00", 0);
        item(7L, null, "冬", "900.00", 1);
        item(8L, null, "夏", "800.00", 0);
        var json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        var mvc = MockMvcBuilders.standaloneSetup(
                        new WardrobeController(service, mock(IWardrobeCategoryService.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/wardrobe/stats").param("userId", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalCount").isNumber())
                    .andExpect(jsonPath("$.data.totalCount").value(1))
                    .andExpect(jsonPath("$.data.categoryCount['未分类']").isNumber())
                    .andExpect(jsonPath("$.data.categoryCount['未分类']").value(1))
                    .andExpect(jsonPath("$.data.seasonCount['春']").isNumber())
                    .andExpect(jsonPath("$.data.seasonCount['春']").value(1))
                    .andExpect(jsonPath("$.data.seasonCount['夏']").value(0))
                    .andExpect(jsonPath("$.data.seasonCount['冬']").value(0))
                    .andExpect(jsonPath("$.data.totalValue").value(10.00))
                    .andExpect(jsonPath("$.data.avgPrice").value(10.00));
        }
    }

    @Test
    void getStats_大金额汇总不损失精度() {
        item(7L, null, "", "99999999.99", 0);
        item(7L, null, "", "99999999.99", 0);
        item(7L, null, null, null, 0);

        var stats = service.getStats(7L);

        assertEquals(new BigDecimal("199999999.98"), stats.getTotalValue());
        assertEquals(new BigDecimal("66666666.66"), stats.getAvgPrice());
    }

    @Test
    void getStats_数量超出响应范围时拒绝静默截断() {
        var mapper = mock(WardrobeItemMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.aggregateStats(7L)).thenReturn(new WardrobeStatsAggregate(
                (long) Integer.MAX_VALUE + 1, BigDecimal.ZERO, 0, 0, 0, 0));

        assertThrows(ArithmeticException.class, () -> service.getStats(7L));
    }

    private void category(long id, String name, int type, Long userId, int deleted) {
        jdbc.update("INSERT INTO wardrobe_category(id,name,category_type,user_id,is_deleted) VALUES (?,?,?,?,?)",
                id, name, type, userId, deleted);
    }

    private void item(long userId, Long categoryId, String season, String price, int deleted) {
        jdbc.update("INSERT INTO wardrobe_item(id,name,user_id,category_id,season,price,is_deleted) VALUES (?,'衣物',?,?,?,?,?)",
                ++nextItemId, userId, categoryId, season, price == null ? null : new BigDecimal(price), deleted);
    }
}
