package top.aiolife.wardrobe.service;

import top.aiolife.record.service.RecordImageService;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.web.CurrentUserIdArgumentResolver;
import top.aiolife.wardrobe.api.WardrobeController;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.dto.WardrobeStatsAggregate;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 自建 PostgreSQL 18 执行衣柜聚合 SQL，验证 jsonb 季节统计、数据隔离与响应数字类型；
 * 不读取本机或业务库连接。
 */
@Tag("integration")
class WardrobeStatsSqlTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18-alpine")
            .withEnv("POSTGRES_PASSWORD", "isolated-wardrobe")
            .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofMinutes(2));
    private static JdbcTemplate jdbc;
    private static SqlSession session;
    private WardrobeItemServiceImpl service;
    private WardrobeCategoryMapper categories;
    private long nextItemId;

    @BeforeAll
    static void 数据库初始化() throws IOException {
        POSTGRES.start();
        var source = new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/fixture?stringtype=unspecified");
        source.setUser("postgres");
        source.setPassword("isolated-wardrobe");
        jdbc = new JdbcTemplate(source);
        // 生产迁移按版本执行；公共种子与本次聚合口径无关，沿用回归测试的做法跳过。
        try (var paths = Files.list(Path.of("sql/migrations"))) {
            for (var path : paths
                    .filter(p -> p.getFileName().toString().matches("V\\d.*\\.sql"))
                    .filter(p -> !p.getFileName().toString().startsWith("V20261110_02_"))
                    .sorted()
                    .toList()) {
                jdbc.execute(Files.readString(path));
            }
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(WardrobeItemMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM wardrobe_item");
        jdbc.update("DELETE FROM wardrobe_category");
        session.clearCache();
        nextItemId = 0;
        categories = mock(WardrobeCategoryMapper.class);
        service = new WardrobeItemServiceImpl(categories, mock(RecordImageService.class));
        ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(WardrobeItemMapper.class));
    }

    @AfterAll
    static void 清理数据库() {
        if (session != null) session.close();
        POSTGRES.stop();
    }

    @Test
    void 季节按jsonb数组读写并按包含语义筛选() {
        var mapper = session.getMapper(WardrobeItemMapper.class);
        var entity = new WardrobeItemEntity();
        entity.setId(9007199254740993L);
        entity.setName("两季衣物");
        entity.setUserId(7L);
        entity.setSeason(List.of("春", "冬"));
        entity.setSourcePlatform("jd");
        entity.setSourceOrderId("order-1");
        entity.setSourceItemId("line:0");
        entity.setSourceUnitIndex(1);
        entity.fillCreateCommonField(7L);
        assertEquals(1, mapper.insert(entity));
        session.commit();
        session.clearCache();

        assertEquals(List.of("春", "冬"), mapper.selectById(entity.getId()).getSeason());
        assertEquals(List.of("春", "冬"), mapper.lockCaptureOrder(7L, "jd", "order-1").getFirst().getSeason());
        assertEquals(1, service.listItems(7L, null, "冬", null).size());
        assertEquals(0, service.listItems(7L, null, "夏", null).size());
    }

    @Test
    void getStats_空衣柜保留四季零值和空分类() {
        var stats = service.getStats(7L);

        assertEquals(0, stats.getTotalCount());
        assertTrue(stats.getCategoryCount().isEmpty());
        assertEquals(Map.of("春", 0, "夏", 0, "秋", 0, "冬", 0), stats.getSeasonCount());
        assertEquals(0, stats.getTotalValue().signum());
        assertEquals(0, stats.getAvgPurchasePrice().signum());
        verifyNoInteractions(categories);
    }

    @Test
    void getStats_同名分类合并且失效和越权分类归未分类() {
        category(11, "上装", null, 0);
        category(12, "上装", 7L, 0);
        category(13, "他人分类", 8L, 0);
        category(14, "已删除分类", null, 1);
        category(15, "A", null, 0);
        category(16, "a", null, 0);
        category(17, "A ", null, 0);
        category(18, "未分类", null, 0);
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
        item(7L, null, "[\"春\",\"春\",\"夏\"]", "2.00", 0);
        item(7L, null, "[\"秋\",\"冬\"]", null, 0);
        item(7L, null, null, null, 0);

        var stats = service.getStats(7L);

        assertEquals(3, stats.getTotalCount());
        assertEquals(Map.of("春", 1, "夏", 1, "秋", 1, "冬", 1), stats.getSeasonCount());
        assertEquals(new BigDecimal("2.00"), stats.getTotalValue());
        assertEquals(new BigDecimal("0.67"), stats.getAvgPurchasePrice());
    }

    @Test
    void getStats_过滤其他用户及删除衣物且HTTP计数仍为数字() throws Exception {
        item(7L, null, "[\"春\"]", "10.00", 0);
        item(7L, null, "[\"冬\"]", "900.00", 1);
        item(8L, null, "[\"夏\"]", "800.00", 0);
        var json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        var mvc = MockMvcBuilders.standaloneSetup(
                        new WardrobeController(service, mock(IWardrobeCategoryService.class)))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            login.when(StpUtil::getLoginIdAsString).thenReturn("7");
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
                    .andExpect(jsonPath("$.data.avgPurchasePrice").value(10.00));
        }
    }

    @Test
    void getStats_多季节衣物按各自季节计数() {
        item(7L, null, "[\"春\",\"秋\"]", "10.00", 0);
        item(7L, null, "[\"夏\",\"冬\"]", "20.00", 0);

        var stats = service.getStats(7L);

        assertEquals(Map.of("春", 1, "夏", 1, "秋", 1, "冬", 1), stats.getSeasonCount());
    }

    @Test
    void getStats_大金额汇总不损失精度() {
        item(7L, null, null, "99999999.99", 0);
        item(7L, null, null, "99999999.99", 0);
        item(7L, null, null, null, 0);

        var stats = service.getStats(7L);

        assertEquals(new BigDecimal("199999999.98"), stats.getTotalValue());
        assertEquals(new BigDecimal("66666666.66"), stats.getAvgPurchasePrice());
    }

    @Test
    void getStats_数量超出响应范围时拒绝静默截断() {
        var mapper = mock(WardrobeItemMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.aggregateStats(7L)).thenReturn(new WardrobeStatsAggregate(
                (long) Integer.MAX_VALUE + 1, BigDecimal.ZERO, 0, 0, 0, 0));

        assertThrows(ArithmeticException.class, () -> service.getStats(7L));
    }

    private void category(long id, String name, Long userId, int deleted) {
        jdbc.update("INSERT INTO wardrobe_category(id,name,user_id,is_deleted) VALUES (?,?,?,?)",
                id, name, userId, deleted);
    }

    private void item(long userId, Long categoryId, String seasonJson, String price, int deleted) {
        jdbc.update("INSERT INTO wardrobe_item(id,name,user_id,category_id,season,purchase_price,is_deleted)"
                        + " VALUES (?,'衣物',?,?,?::jsonb,?,?)",
                ++nextItemId, userId, categoryId, seasonJson,
                price == null ? null : new BigDecimal(price), deleted);
    }
}
