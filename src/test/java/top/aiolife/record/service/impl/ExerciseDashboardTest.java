package top.aiolife.record.service.impl;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.vo.ExerciseDashboardItemVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardTrendPointVO;
import top.aiolife.record.service.UserDictDataService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际执行 Mapper SQL，在独立 H2 数据库验证聚合、历史窗口和日期分页。 */
class ExerciseDashboardTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private ExerciseRecordServiceImpl service;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:exercise_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE exercise_record (user_id BIGINT, exercise_type_id BIGINT, exercise_date DATE, exercise_count INT, is_deleted INT DEFAULT 0)");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(IExerciseRecordMapper.class);
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        UserDictDataService dicts = mock(UserDictDataService.class);
        when(dicts.listUserVisibleDictData(anyLong(), anyString())).thenReturn(List.of());
        service = new ExerciseRecordServiceImpl(dicts);
        ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(IExerciseRecordMapper.class));
    }

    @AfterEach
    void tearDown() {
        session.close();
        jdbc.execute("DROP ALL OBJECTS");
    }

    private void add(long user, long type, LocalDate date, int count, int deleted) {
        jdbc.update("INSERT INTO exercise_record VALUES (?, ?, ?, ?, ?)", user, type, date, count, deleted);
    }

    @Test
    void testTrend_五次按时间升序且同日合计并隔离用户和删除记录() {
        for (int i = 0; i < 7; i++) add(1, 10, today.minusDays(i * 3L), 70 - i * 10, 0);
        add(1, 10, today, 5, 0);
        add(2, 10, today, 900, 0);
        add(1, 10, today, 800, 1);
        add(1, 10, today.plusDays(1), 700, 0);
        ExerciseDashboardItemVO item = service.getDashboardSummary(1L, null, 1).getDays().getFirst().getItems().getFirst();
        assertEquals(75, item.getCount());
        assertEquals(60, item.getPrevCount());
        assertEquals(15, item.getDeltaCount());
        assertEquals(List.of(30, 40, 50, 60, 75), item.getTrend().stream().map(ExerciseDashboardTrendPointVO::count).toList());
        assertEquals(today.minusDays(12), item.getTrend().getFirst().date());
        assertEquals(today, item.getTrend().getLast().date());
    }

    @Test
    void testHistory_低频类型不被五百条高频记录挤掉() {
        add(1, 10, today, 50, 0);
        add(1, 20, today, 100, 0);
        for (int i = 1; i <= 510; i++) add(1, 20, today.minusDays(i), i, 0);
        for (int i = 1; i <= 5; i++) add(1, 10, today.minusDays(600L + i), 50 - i, 0);
        var result = service.getDashboardSummary(1L, null, 1);
        var item = result.getDays().getFirst().getItems().stream().filter(row -> row.getExerciseTypeId() == 10L).findFirst().orElseThrow();
        assertEquals(List.of(46, 47, 48, 49, 50), item.getTrend().stream().map(ExerciseDashboardTrendPointVO::count).toList());
        assertEquals(1, item.getDeltaCount());
    }

    @Test
    void testPagination_一天多于五十条仍完整且连续日期不跳过() {
        for (int i = 0; i < 60; i++) add(1, 10, today, 1, 0);
        for (int i = 1; i <= 7; i++) add(1, 10, today.minusDays(i), i, 0);
        var first = service.getDashboardSummary(1L, null, 2);
        assertEquals(60, first.getDays().getFirst().getItems().getFirst().getCount());
        assertTrue(first.getHasMore());
        var second = service.getDashboardSummary(1L, first.getLastDate(), 2);
        assertEquals(today.minusDays(2), second.getDays().getFirst().getDate());
        var row = second.getDays().getFirst().getItems().getFirst();
        assertEquals(List.of(6, 5, 4, 3, 2), row.getTrend().stream().map(ExerciseDashboardTrendPointVO::count).toList());
        assertEquals(-1, row.getDeltaCount());
    }

    @Test
    void testShortHistory_零值和单点不补齐且空页正常() {
        add(1, 10, today, 0, 0);
        var result = service.getDashboardSummary(1L, null, 7);
        assertFalse(result.getHasMore());
        assertNull(result.getLastDate());
        var row = result.getDays().getFirst().getItems().getFirst();
        assertEquals(List.of(new ExerciseDashboardTrendPointVO(today, 0)), row.getTrend());
        assertNull(row.getPrevCount());
        assertNull(row.getDeltaCount());
        var empty = service.getDashboardSummary(1L, today.minusDays(1), 7);
        assertTrue(empty.getDays().isEmpty());
        assertFalse(empty.getHasMore());
    }
}
