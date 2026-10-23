package top.aiolife.record.service;

import cn.dev33.satoken.stp.StpUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.api.FinanceCalendarController;
import top.aiolife.record.mapper.FinanceCalendarMapper;
import top.aiolife.record.pojo.vo.FinanceCalendarVO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H2 执行生产聚合 SQL，验证日历边界、归属、金额及 HTTP 契约。 */
class FinanceCalendarServiceTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private FinanceCalendarService service;
    private final HomeSectionAccessGuard access = mock(HomeSectionAccessGuard.class);
    private final LocalDate today = LocalDate.of(2026, 10, 22);

    @BeforeEach void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:expense_calendar_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE expense (user_id BIGINT, amt DECIMAL(12,2), exp_time TIMESTAMP, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE income (user_id BIGINT, amt DECIMAL(12,2), inc_date DATE, is_deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO income VALUES (7,100.10,'2026-10-01',0),(7,20.20,'2026-10-04',0),(7,30.30,'2026-10-05',0),(7,40.40,'2026-11-01',0),(7,50.50,NULL,0),(8,900,'2026-10-01',0),(7,900,'2026-10-01',1)");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(FinanceCalendarMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        service = new FinanceCalendarService(session.getMapper(FinanceCalendarMapper.class), access);
        jdbc.update("INSERT INTO expense VALUES (7,10.10,'2026-10-01 00:00:00',0),(7,2.20,'2026-10-01 23:59:59',0),(7,3.30,'2026-10-04 12:00:00',0),(7,4.40,'2026-10-05 00:00:00',0),(7,5.50,'2026-10-31 23:59:59',0),(7,6.60,'2026-11-01 00:00:00',0),(7,7.70,'2025-12-31 23:59:59',0),(7,8.80,NULL,0),(8,900,'2026-10-01 12:00:00',0),(7,900,'2026-10-01 12:00:00',1)");
    }
    @AfterEach void cleanup() { session.close(); jdbc.execute("DROP ALL OBJECTS"); }

    @Test void 日周合计一致且跨月周不重复纳入边界外账目() {
        var day = service.calendar(7, "day", "2026-10", today);
        var week = service.calendar(7, "week", "2026-10", today);
        assertEquals(31, day.buckets().size());
        assertEquals(new BigDecimal("25.50"), day.amount());
        assertEquals(day.amount(), week.amount());
        assertEquals(new BigDecimal("12.30"), day.buckets().getFirst().amount());
        assertEquals(5, week.buckets().size());
        assertEquals(new BigDecimal("15.60"), week.buckets().getFirst().amount());
        assertEquals(LocalDate.of(2026, 10, 5), week.buckets().getFirst().endDate());
        assertEquals(LocalDate.of(2026, 11, 1), week.buckets().getLast().endDate());
        assertEquals(new BigDecimal("5.50"), week.buckets().getLast().amount());
        assertTrue(day.buckets().getLast().future());
    }

    @Test void 月年聚合保留精确金额并显式包含未填日期账目() {
        var month = service.calendar(7, "month", "2026", today);
        assertEquals(12, month.buckets().size());
        assertEquals(new BigDecimal("32.10"), month.amount());
        assertEquals(new BigDecimal("25.50"), month.buckets().get(9).amount());
        var year = service.calendar(7, "year", null, today);
        assertEquals(new BigDecimal("48.60"), year.amount());
        assertEquals(List.of("2025年", "2026年", "未填日期"), year.buckets().stream().map(FinanceCalendarVO.Bucket::label).toList());
        assertEquals(new BigDecimal("8.80"), year.buckets().getLast().amount());
        assertNull(year.startDate());
    }

    @Test void 空账目闰月和六周月份保留完整日历结构() {
        var leap = service.calendar(99, "day", "2024-02", today);
        assertEquals(29, leap.buckets().size());
        assertEquals(0, leap.amount().signum());
        var weeks = service.calendar(99, "week", "2026-03", today);
        assertEquals(6, weeks.buckets().size());
        assertEquals(LocalDate.of(2026, 3, 2), weeks.buckets().getFirst().endDate());
        assertEquals(1, service.calendar(99, "year", null, today).buckets().size());
    }

    @Test void HTTP日期校验身份隔离以及二级锁() throws Exception {
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var mvc = MockMvcBuilders.standaloneSetup(new FinanceCalendarController(service))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/home/sections/expense/calendar").param("granularity", "day").param("date", "2026-10").param("userId", "8"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.amount").value(25.50))
                    .andExpect(jsonPath("$.data.buckets[0].amount").value(12.30))
                    .andExpect(jsonPath("$.data.buckets[0].startDate").value("2026-10-01"));
            for (String date : List.of("2026-13", "2026-2", "2026-02-30", "0000-01", "9999-01")) {
                mvc.perform(get("/home/sections/expense/calendar").param("date", date)).andExpect(status().isBadRequest());
            }
            mvc.perform(get("/home/sections/expense/calendar").param("granularity", "wrong")).andExpect(status().isBadRequest());
            mvc.perform(get("/home/sections/expense/calendar").param("granularity", "year").param("date", "2026")).andExpect(status().isBadRequest());
            doThrow(new SecondaryLockRequiredException("/finance/expense")).when(access).check(7, "/finance/expense");
            mvc.perform(get("/home/sections/expense/calendar"))
                    .andExpect(jsonPath("$.code").value(2001)).andExpect(jsonPath("$.data.buckets").doesNotExist());
        }
    }
    @Test void 收入按周月年聚合且保留独立权限边界() throws Exception {
        var week = service.incomeCalendar(7, "week", "2026-10", today);
        assertEquals(new BigDecimal("150.60"), week.amount());
        assertEquals(new BigDecimal("120.30"), week.buckets().getFirst().amount());
        assertEquals(5, week.buckets().size());
        assertEquals(new BigDecimal("191.00"), service.incomeCalendar(7, "month", "2026", today).amount());
        assertEquals(new BigDecimal("241.50"), service.incomeCalendar(7, "year", null, today).amount());
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var mvc = MockMvcBuilders.standaloneSetup(new FinanceCalendarController(service))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/home/sections/income/calendar").param("date", "2026-10").param("userId", "8"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.granularity").value("week"))
                    .andExpect(jsonPath("$.data.amount").value(150.60))
                    .andExpect(jsonPath("$.data.buckets[0].endDate").value("2026-10-05"));
            verify(access).check(7, "/finance/income");
            mvc.perform(get("/home/sections/income/calendar").param("granularity", "day"))
                    .andExpect(status().isBadRequest());
            doThrow(new SecondaryLockRequiredException("/finance/income")).when(access).check(7, "/finance/income");
            mvc.perform(get("/home/sections/income/calendar"))
                    .andExpect(jsonPath("$.code").value(2001)).andExpect(jsonPath("$.data.buckets").doesNotExist());
        }
    }
    @Test void 概览使用共同范围计算收支结余并校验三项权限() throws Exception {
        var week = service.overviewCalendar(7, "week", "2026-10", today);
        assertEquals(new BigDecimal("150.60"), week.income());
        assertEquals(new BigDecimal("25.50"), week.expense());
        assertEquals(new BigDecimal("125.10"), week.balance());
        assertEquals(week.balance(), week.amount());
        assertEquals(new BigDecimal("104.70"), week.buckets().getFirst().balance());
        assertEquals(new BigDecimal("120.30"), week.buckets().getFirst().income());
        assertEquals(new BigDecimal("15.60"), week.buckets().getFirst().expense());
        var year = service.overviewCalendar(7, "year", null, today);
        assertEquals(new BigDecimal("192.90"), year.balance());
        assertEquals(List.of("2025年", "2026年", "未填日期"), year.buckets().stream().map(row -> row.label()).toList());
        assertEquals(new BigDecimal("-7.70"), year.buckets().getFirst().balance());
        assertEquals(new BigDecimal("41.70"), year.buckets().getLast().balance());
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var mvc = MockMvcBuilders.standaloneSetup(new FinanceCalendarController(service))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/home/sections/finance/calendar").param("date", "2026-10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.balance").value(125.10))
                    .andExpect(jsonPath("$.data.buckets[0].expense").value(15.60));
            verify(access).check(7, "/finance/dashboard", "/finance/income", "/finance/expense");
            mvc.perform(get("/home/sections/finance/calendar").param("granularity", "day")).andExpect(status().isBadRequest());
            doThrow(new SecondaryLockRequiredException("/finance/expense")).when(access)
                    .check(7, "/finance/dashboard", "/finance/income", "/finance/expense");
            mvc.perform(get("/home/sections/finance/calendar"))
                    .andExpect(jsonPath("$.code").value(2001)).andExpect(jsonPath("$.data.buckets").doesNotExist());
        }
    }
}
