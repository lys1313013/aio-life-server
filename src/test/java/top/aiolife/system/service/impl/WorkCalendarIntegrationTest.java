package top.aiolife.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.service.impl.TimeRecordServiceImpl;
import top.aiolife.system.mapper.IWorkCalendarMapper;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅连接显式指定端口的本地临时 MySQL/Redis，不启动应用或读取正式环境配置。 */
@EnabledIfEnvironmentVariable(named = "AIO_CALENDAR_TEST_MYSQL_PORT", matches = "[0-9]+")
@EnabledIfEnvironmentVariable(named = "AIO_CALENDAR_TEST_REDIS_PORT", matches = "[0-9]+")
class WorkCalendarIntegrationTest {
    @Test
    void testCalendar_真实SQL及Redis缓存与推荐链路() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"
                + System.getenv("AIO_CALENDAR_TEST_MYSQL_PORT")
                + "/aio_life?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", "root", "");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/1_init_table/2026-09-22_add_sys_work_calendar.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("sql/2_ini_data/2026-09-22_sys_work_calendar_2026.sql"));
        }
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE time_record (
                    id VARCHAR(32) PRIMARY KEY, user_id BIGINT, date DATE, category_id BIGINT,
                    start_time INT, end_time INT, is_deleted INT DEFAULT 0
                )
                """);
        jdbc.execute("""
                INSERT INTO time_record (id,user_id,date,category_id,start_time,end_time,is_deleted) VALUES
                ('1',1,'2026-09-20',104,600,699,0),
                ('2',1,'2026-09-20',106,700,799,0),
                ('3',1,'2026-09-25',105,600,699,0),
                ('4',1,'2026-09-25',104,900,999,0),
                ('5',1,'2026-09-25',107,1000,1099,0),
                ('6',2,'2026-09-20',999,600,699,0),
                ('7',1,'2026-09-20',999,600,699,1),
                ('8',1,'2025-12-30',999,600,699,0)
                """);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IWorkCalendarMapper.class);
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/ITimeRecordMapper.xml"));
        var factory = factoryBean.getObject();
        assertNotNull(factory);
        var redisFactory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("AIO_CALENDAR_TEST_REDIS_PORT")));
        redisFactory.afterPropertiesSet();
        try (var session = factory.openSession(true)) {
            var redis = new StringRedisTemplate(redisFactory);
            var calendarMapper = spy(session.getMapper(IWorkCalendarMapper.class));
            var calendar = new WorkCalendarServiceImpl(calendarMapper, redis);
            var date = LocalDate.of(2026, 9, 21);
            var key = "workCalendar:prevDate:2026-09-21";
            assertEquals(LocalDate.of(2026, 9, 20), calendar.findPreviousComparableDate(date));
            assertEquals("2026-09-20", redis.opsForValue().get(key));
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 172_740 && ttl <= 172_800, "TTL 应为 2 天");
            clearInvocations(calendarMapper);
            assertEquals(LocalDate.of(2026, 9, 20), calendar.findPreviousComparableDate(date));
            verifyNoInteractions(calendarMapper);

            var timeMapper = session.getMapper(ITimeRecordMapper.class);
            var categories = mock(top.aiolife.record.service.ITimeTrackerCategoryService.class);
            when(categories.listUserVisibleCategories(1L)).thenReturn(java.util.stream.Stream.of(104L, 105L, 106L, 107L)
                    .map(id -> {
                        var category = new top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity();
                        category.setId(id);
                        category.setName("分类" + id);
                        return category;
                    }).toList());
            var service = new TimeRecordServiceImpl(timeMapper, null, null, null, calendar, org.mockito.Mockito.mock(top.aiolife.record.prediction.JevCategoryRecommendationService.class, invocation -> null), new top.aiolife.record.prediction.RecommendationDataCache(15000), categories, org.mockito.Mockito.mock(top.aiolife.sso.service.SecondaryLockGuard.class));
            ReflectionTestUtils.setField(service, "baseMapper", timeMapper);
            try (var clock = mockStatic(java.time.LocalDateTime.class, CALLS_REAL_METHODS)) {
                var now = java.time.LocalDateTime.of(2026, 12, 31, 12, 0);
                clock.when(java.time.LocalDateTime::now).thenReturn(now);
                assertEquals(104L, service.recommendType(1L, "2026-09-21", 600, null));
                assertEquals(105L, service.recommendType(1L, "2026-09-26", 600, null));
                assertEquals(1, timeMapper.findReferenceRecords(1L, "2026-09-20", 600, 1440).size());
                assertTrue(timeMapper.findReferenceRecords(1L, "2026-09-20", 600, 699).isEmpty());
                assertTrue(timeMapper.findReferenceRecords(1L, "2026-09-19", 600, 1440).isEmpty());
                // 单实体查询改为列表，重叠数据不会抛 TooManyResultsException。
                jdbc.update("INSERT INTO time_record VALUES ('9',1,'2026-09-20',106,600,699,0)");
                session.clearCache();
                assertEquals(2, timeMapper.findReferenceRecords(1L, "2026-09-20", 601, 1440).size());
                assertNull(service.recommendType(1L, "2026-09-21", 601, null));
            }

            // 修正日历后只清除受影响的目标日期缓存，下次请求重新查库。
            jdbc.update("UPDATE sys_work_calendar SET day_type=1 WHERE calendar_date='2026-09-20'");
            session.clearCache();
            calendar.evictPreviousComparableDate(date);
            assertEquals(LocalDate.of(2026, 9, 18), calendar.findPreviousComparableDate(date));

            assertNull(calendar.findPreviousComparableDate(LocalDate.of(2026, 1, 1)));
            assertFalse(Boolean.TRUE.equals(redis.hasKey("workCalendar:prevDate:2026-01-01")));
            assertNull(service.recommendType(1L, "2027-01-04", 600, null));
        } finally {
            redisFactory.destroy();
        }
    }
}
