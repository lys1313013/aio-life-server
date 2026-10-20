package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import top.aiolife.config.JsonConfig;
import top.aiolife.record.api.TimeRecordController;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.req.ExerciseRecordReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.prediction.JevCategoryRecommendationService;
import top.aiolife.record.prediction.RecommendationDataCache;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.service.IWorkCalendarService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 独立 H2 执行真实 Mapper、批量插入及事务，验证明细身份、归属与失败回滚。 */
class TimeRecordExercisePersistenceTest {
    private static final long FIRST_ID = 9223372036854775001L;
    private static final long SECOND_ID = 9223372036854775002L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private JdbcTemplate jdbc;
    private TimeRecordServiceImpl service;
    private TimeRecordController controller;
    private SecondaryLockGuard guard;
    private MockedStatic<StpUtil> login;
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:time_exercises_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE time_record (
                    id VARCHAR(32) PRIMARY KEY, user_id BIGINT, category_id BIGINT, date DATE,
                    start_time INT, end_time INT, title VARCHAR(255), description VARCHAR(255), duration INT,
                    relate_type INT, relate_id BIGINT, is_manual BIGINT, is_deleted INT DEFAULT 0,
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE exercise_record (
                    id BIGINT PRIMARY KEY, user_id BIGINT, time_id VARCHAR(32), exercise_type_id BIGINT,
                    exercise_date DATE, exercise_count INT CHECK (exercise_count >= 0), description VARCHAR(255),
                    is_deleted INT DEFAULT 0, create_user BIGINT, update_user BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ITimeRecordMapper.class);
        configuration.addMapper(IExerciseRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var mapper = session.getMapper(ITimeRecordMapper.class);
        var exercises = new ExerciseRecordServiceImpl(mock(UserDictDataService.class));
        ReflectionTestUtils.setField(exercises, "baseMapper", session.getMapper(IExerciseRecordMapper.class));
        guard = mock(SecondaryLockGuard.class);
        var categories = mock(ITimeTrackerCategoryService.class);
        var target = new TimeRecordServiceImpl(mapper, exercises, mock(IReadRecordService.class),
                mock(IMovieService.class), mock(IWorkCalendarService.class), mock(JevCategoryRecommendationService.class),
                new RecommendationDataCache(15000), categories, guard);
        ReflectionTestUtils.setField(target, "baseMapper", mapper);
        ReflectionTestUtils.setField(target, "timeRecordCacheManager", new ConcurrentMapCacheManager("timeRecordDay:v1"));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (TimeRecordServiceImpl) proxy.getProxy();
        controller = new TimeRecordController(guard, service, exercises, categories, mock(UserDictDataService.class));
        login = mockStatic(StpUtil.class);
        login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        jdbc.update("INSERT INTO time_record (id, user_id, date, title) VALUES ('time-1', 1, ?, '原始时迹')", DATE);
        addExercise(FIRST_ID, 1L, "time-1");
        addExercise(SECOND_ID, 1L, "time-1");
    }

    @AfterEach
    void cleanup() {
        if (login != null) login.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void update_未变化与重复保存不增加记录且保留全部审计字段() {
        var before = jdbc.queryForList("SELECT * FROM exercise_record ORDER BY id");
        save(List.of(exercise(SECOND_ID, 10), exercise(FIRST_ID, 10)), DATE);
        save(List.of(exercise(FIRST_ID, 10), exercise(SECOND_ID, 10)), DATE);
        assertEquals(before, jdbc.queryForList("SELECT * FROM exercise_record ORDER BY id"));
        verify(guard, times(2)).checkMenus(1L, "/record/exercise");
    }

    @Test
    void update_同类型多组按ID分别更新且只插入新增删除移除项() {
        var added = exercise(null, 30);
        save(List.of(exercise(SECOND_ID, 20), added), DATE);
        assertEquals(3, totalCount());
        assertEquals(1, jdbc.queryForObject("SELECT is_deleted FROM exercise_record WHERE id = ?", Integer.class, FIRST_ID));
        assertEquals(20, jdbc.queryForObject("SELECT exercise_count FROM exercise_record WHERE id = ?", Integer.class, SECOND_ID));
        assertEquals("2000-01-01 00:00:00.0", jdbc.queryForObject(
                "SELECT create_time FROM exercise_record WHERE id = ?", Timestamp.class, SECOND_ID).toString());
        assertEquals(2, activeCount());
        var detail = controller.getById("time-1").getData().getExercises();
        var newId = detail.stream().filter(e -> e.getExerciseCount() == 30).findFirst().orElseThrow().getId();
        save(List.of(exercise(SECOND_ID, 20), exercise(newId, 30)), DATE);
        assertEquals(3, totalCount(), "回传新ID后再次保存不新增或重建");
    }

    @Test
    void update_修改类型日期并清空次数描述时保持ID() {
        var changed = exercise(FIRST_ID, null);
        changed.setExerciseTypeId(88L);
        changed.setDescription(null);
        save(List.of(changed, exercise(SECOND_ID, 10)), DATE.plusDays(1));
        var detail = controller.getById("time-1").getData().getExercises();
        var first = detail.stream().filter(e -> e.getId() == FIRST_ID).findFirst().orElseThrow();
        assertEquals(88L, first.getExerciseTypeId());
        assertNull(first.getExerciseCount());
        assertNull(first.getDescription());
        assertTrue(detail.stream().allMatch(e -> DATE.plusDays(1).equals(e.getExerciseDate())));
        assertEquals(2, totalCount());
    }

    @Test
    void update_未传日期使用时迹原日期() {
        save(List.of(exercise(FIRST_ID, 11), exercise(SECOND_ID, 10)), null);
        assertTrue(controller.getById("time-1").getData().getExercises().stream()
                .allMatch(e -> DATE.equals(e.getExerciseDate())));
    }

    @Test
    void update_空列表及未传明细维持清空语义且隔离其他用户时迹() {
        addExercise(3L, 2L, "time-1");
        addExercise(4L, 1L, "time-2");
        save(List.of(), DATE);
        save(null, DATE);
        assertEquals(4, totalCount());
        assertEquals(0, activeCount());
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM exercise_record WHERE id = 3", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM exercise_record WHERE id = 4", Integer.class));
    }

    @Test
    void update_拒绝其他用户其他时迹已删除不存在重复及非法ID并回滚父记录() {
        addExercise(3L, 2L, "time-1");
        addExercise(4L, 1L, "time-2");
        addExercise(5L, 1L, "time-1");
        jdbc.update("UPDATE exercise_record SET is_deleted = 1 WHERE id = 5");
        for (String invalid : List.of("3", "4", "5", "999", "invalid", "")) {
            var request = exercise(FIRST_ID, 99);
            request.setId(invalid);
            assertThrows(IllegalArgumentException.class, () -> save(List.of(request), DATE));
            assertOriginal();
        }
        assertThrows(IllegalArgumentException.class,
                () -> save(List.of(exercise(FIRST_ID, 20), exercise(FIRST_ID, 30)), DATE));
        assertOriginal();
    }

    @Test
    void update_插入失败回滚已有运动更新删除与时迹修改() {
        assertThrows(RuntimeException.class,
                () -> save(List.of(exercise(FIRST_ID, 20), exercise(null, -1)), DATE));
        assertOriginal();
        assertEquals(2, totalCount());
    }

    @Test
    void update_二级锁失败回滚时迹且不修改运动() {
        doThrow(new IllegalStateException("locked")).when(guard).checkMenus(1L, "/record/exercise");
        assertThrows(IllegalStateException.class, () -> save(List.of(exercise(FIRST_ID, 20)), DATE));
        assertOriginal();
    }

    @Test
    void http_详情返回字符串ID且请求映射保留ID用于更新() throws Exception {
        var details = json.valueToTree(controller.getById("time-1").getData()).get("exercises");
        assertTrue(details.get(0).get("id").isTextual());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        mvc.perform(put("/timeRecord/time-1").contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"2026-10-20","exercises":[
                  {"id":"9223372036854775001","exerciseTypeId":"77","exerciseCount":21,"description":null},
                  {"id":"9223372036854775002","exerciseTypeId":"77","exerciseCount":10,"description":"原备注"}]}
                """)).andExpect(status().isOk());
        assertEquals(21, jdbc.queryForObject("SELECT exercise_count FROM exercise_record WHERE id = ?", Integer.class, FIRST_ID));
        assertEquals(2, totalCount());
    }

    private void save(List<ExerciseRecordReq> exercises, LocalDate date) {
        var request = new TimeRecordReq();
        request.setId("time-1");
        request.setDate(date);
        request.setTitle("更新时迹");
        request.setExercises(exercises);
        service.updateTimeRecord(request);
    }

    private ExerciseRecordReq exercise(Long id, Integer count) {
        var request = new ExerciseRecordReq();
        request.setId(id == null ? null : id.toString());
        request.setExerciseTypeId(77L);
        request.setExerciseCount(count);
        request.setDescription("原备注");
        return request;
    }

    private void addExercise(long id, long userId, String timeId) {
        jdbc.update("""
                INSERT INTO exercise_record (id, user_id, time_id, exercise_type_id, exercise_date,
                    exercise_count, description, create_user, update_user, create_time, update_time)
                VALUES (?, ?, ?, 77, ?, 10, '原备注', ?, ?, '2000-01-01 00:00:00', '2000-01-01 00:00:00')
                """, id, userId, timeId, DATE, userId, userId);
    }

    private int totalCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exercise_record", Integer.class);
    }

    private int activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exercise_record WHERE user_id = 1 AND time_id = 'time-1' AND is_deleted = 0", Integer.class);
    }

    private void assertOriginal() {
        assertEquals("原始时迹", jdbc.queryForObject("SELECT title FROM time_record WHERE id = 'time-1'", String.class));
        assertEquals(2, activeCount());
        assertEquals(10, jdbc.queryForObject("SELECT exercise_count FROM exercise_record WHERE id = ?", Integer.class, FIRST_ID));
    }
}
