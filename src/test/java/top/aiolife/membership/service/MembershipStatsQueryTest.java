package top.aiolife.membership.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.config.JsonConfig;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.pojo.dto.MembershipStatsAggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 执行真实 Mapper 聚合，覆盖日期、成本口径、逻辑删除和响应数字类型。 */
class MembershipStatsQueryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private SqlSession session;
    private JdbcTemplate jdbc;
    private MembershipRecordWriteService service;

    @BeforeEach
    void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:membership_stats_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE membership_record (
                    user_id BIGINT NOT NULL,
                    start_date DATE,
                    expiry_date DATE NOT NULL,
                    monthly_amount DECIMAL(10,2),
                    is_deleted INT NOT NULL DEFAULT 0
                )
                """);
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(IMembershipMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        service = new MembershipRecordWriteService(session.getMapper(IMembershipMapper.class), mock(MembershipProviderService.class));
    }

    @AfterEach
    void cleanup() {
        session.close();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void 到期日边界和月均成本保留原口径且隔离其他用户和已删除记录() {
        jdbc.update("""
                INSERT INTO membership_record(user_id,start_date,expiry_date,monthly_amount,is_deleted) VALUES
                  (7,NULL,'2026-10-27',99.99,0),
                  (7,NULL,'2026-10-28',10.10,0),
                  (7,'2026-10-28','2026-11-04',20.20,0),
                  (7,'2026-10-28','2026-11-05',30.30,0),
                  (7,NULL,'2026-10-31',40.40,0),
                  (7,'2026-10-29','2026-11-05',50.50,0),
                  (7,NULL,'2026-11-05',NULL,0),
                  (7,NULL,'9999-12-31',1.11,0),
                  (8,NULL,'2026-10-28',999.99,0),
                  (7,NULL,'2026-10-28',999.99,1)
                """);

        var result = service.stats(7, TODAY);

        assertEquals(4, result.getActiveCount());
        assertEquals(3, result.getExpiringCount());
        assertEquals(1, result.getExpiredCount());
        assertEquals(2, result.getExpiringThisMonthCount());
        assertEquals(new BigDecimal("102.11"), result.getMonthlyAmount());
    }

    @Test
    void 空集返回零而非空统计结果() {
        var result = service.stats(7, TODAY);

        assertEquals(0, result.getActiveCount());
        assertEquals(0, result.getExpiringCount());
        assertEquals(0, result.getExpiredCount());
        assertEquals(0, result.getExpiringThisMonthCount());
        assertEquals(0, result.getMonthlyAmount().signum());
    }

    @Test
    void 闰月月底和跨月提醒区间分别计算() {
        jdbc.update("""
                INSERT INTO membership_record(user_id,start_date,expiry_date,monthly_amount,is_deleted) VALUES
                  (7,NULL,'2024-02-29',0.10,0),
                  (7,NULL,'2024-03-01',0.20,0),
                  (7,NULL,'2024-03-06',0.30,0),
                  (7,NULL,'2024-03-07',0.40,0)
                """);

        var result = service.stats(7, LocalDate.of(2024, 2, 28));

        assertEquals(1, result.getActiveCount());
        assertEquals(3, result.getExpiringCount());
        assertEquals(0, result.getExpiredCount());
        assertEquals(1, result.getExpiringThisMonthCount());
        assertEquals(new BigDecimal("1.00"), result.getMonthlyAmount());
    }

    @Test
    void 聚合数量仍输出JSON数字且不允许溢出截断() throws Exception {
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var tree = json.valueToTree(service.stats(7, TODAY));
        assertTrue(tree.path("activeCount").isInt());
        assertTrue(tree.path("expiringCount").isInt());
        assertTrue(tree.path("expiredCount").isInt());
        assertTrue(tree.path("expiringThisMonthCount").isInt());

        var mapper = mock(IMembershipMapper.class);
        var overflow = new MembershipStatsAggregate();
        overflow.setActiveCount((long) Integer.MAX_VALUE + 1);
        when(mapper.aggregateStats(7, TODAY, TODAY.plusDays(7), TODAY.withDayOfMonth(31))).thenReturn(overflow);
        var overflowService = new MembershipRecordWriteService(mapper, mock(MembershipProviderService.class));
        assertThrows(ArithmeticException.class, () -> overflowService.stats(7, TODAY));
    }
}
