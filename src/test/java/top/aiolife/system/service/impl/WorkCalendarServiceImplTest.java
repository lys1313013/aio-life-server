package top.aiolife.system.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.aiolife.system.mapper.IWorkCalendarMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkCalendarServiceImplTest {
    private final IWorkCalendarMapper mapper = mock(IWorkCalendarMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> cache = new HashMap<>();
    private final TreeMap<LocalDate, Integer> days = new TreeMap<>();
    private WorkCalendarServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        var pattern = Pattern.compile("\\('([0-9-]+)', ([0-3]),");
        var matcher = pattern.matcher(Files.readString(Path.of("sql/2_ini_data/2026-09-22_sys_work_calendar_2026.sql")));
        while (matcher.find()) {
            days.put(LocalDate.parse(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
        assertEquals(365, days.size());
        when(mapper.selectDayType(any())).thenAnswer(inv -> days.get(inv.getArgument(0)));
        when(mapper.selectPreviousComparableDate(any(), anyBoolean())).thenAnswer(inv ->
                days.headMap(inv.getArgument(0), false).descendingMap().entrySet().stream()
                        .filter(e -> (e.getValue() == 0 || e.getValue() == 3) == (boolean) inv.getArgument(1))
                        .map(Map.Entry::getKey).findFirst().orElse(null));
        when(mapper.countDatesBetween(any(), any())).thenAnswer(inv ->
                (long) days.subMap(inv.getArgument(0), true, inv.getArgument(1), false).size());
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv -> cache.get(inv.getArgument(0)));
        doAnswer(inv -> {
            cache.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(inv -> cache.remove(inv.getArgument(0)) != null);
        service = new WorkCalendarServiceImpl(mapper, redis);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-21,2026-09-20,true", "2026-09-20,2026-09-18,true",
            "2026-09-25,2026-09-19,false", "2026-09-26,2026-09-25,false",
            "2026-09-27,2026-09-26,false", "2026-10-08,2026-09-30,true",
            "2026-10-10,2026-10-09,true", "2026-10-11,2026-10-07,false",
            "2026-02-14,2026-02-13,true", "2026-02-15,2026-02-08,false",
            "2026-02-24,2026-02-14,true", "2026-01-03,2026-01-02,false",
            "2026-09-22,2026-09-21,true", "2026-09-19,2026-09-13,false"
    })
    void testComparableDate_使用实际年度日历处理放假和补班(String date, String previous, boolean workday) {
        assertEquals(workday, service.isWorkday(LocalDate.parse(date)));
        assertEquals(LocalDate.parse(previous), service.findPreviousComparableDate(LocalDate.parse(date)));
    }

    @Test
    void testCache_按目标日期缓存两天且命中不访问数据库() {
        LocalDate date = LocalDate.of(2026, 9, 21);
        assertEquals(LocalDate.of(2026, 9, 20), service.findPreviousComparableDate(date));
        verify(values).set("workCalendar:prevDate:2026-09-21", "2026-09-20", Duration.ofDays(2));
        clearInvocations(mapper);
        assertEquals(LocalDate.of(2026, 9, 20), service.findPreviousComparableDate(date));
        verifyNoInteractions(mapper);
        assertEquals(LocalDate.of(2026, 9, 21), service.findPreviousComparableDate(date.plusDays(1)));
        assertEquals(2, cache.size());
    }

    @Test
    void testCache_清除指定日期缓存后重新读取变更数据() {
        LocalDate date = LocalDate.of(2026, 9, 21);
        service.findPreviousComparableDate(date);
        days.put(LocalDate.of(2026, 9, 20), 1);
        service.evictPreviousComparableDate(date);
        assertEquals(LocalDate.of(2026, 9, 18), service.findPreviousComparableDate(date));
    }

    @Test
    void testIsWorkday_普通工作日判断不使用Redis缓存() {
        assertTrue(service.isWorkday(LocalDate.of(2026, 9, 20)));
        days.put(LocalDate.of(2026, 9, 20), 1);
        assertFalse(service.isWorkday(LocalDate.of(2026, 9, 20)));
        verifyNoInteractions(values);
    }

    @Test
    void testComparableDate_跨年查询() {
        days.put(LocalDate.of(2025, 12, 31), 0);
        assertEquals(LocalDate.of(2025, 12, 31), service.findPreviousComparableDate(LocalDate.of(2026, 1, 4)));
    }

    @Test
    void testMissing_目标年份缺失不猜测也不缓存空值() {
        assertNull(service.isWorkday(LocalDate.of(2027, 1, 4)));
        assertNull(service.findPreviousComparableDate(LocalDate.of(2027, 1, 4)));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void testMissing_没有历史同类日返回空() {
        assertNull(service.findPreviousComparableDate(LocalDate.of(2026, 1, 1)));
        assertTrue(cache.isEmpty());
    }

    @Test
    void testMissing_中途缺失不跳过未知日期且补齐立即可查() {
        days.remove(LocalDate.of(2026, 9, 19));
        assertNull(service.findPreviousComparableDate(LocalDate.of(2026, 9, 25)));
        assertTrue(cache.isEmpty());
        days.put(LocalDate.of(2026, 9, 19), 1);
        assertEquals(LocalDate.of(2026, 9, 19), service.findPreviousComparableDate(LocalDate.of(2026, 9, 25)));
    }

    @Test
    void testLeapYear_二月二十九日参与查询() {
        days.put(LocalDate.of(2024, 2, 29), 0);
        days.put(LocalDate.of(2024, 3, 1), 0);
        assertEquals(LocalDate.of(2024, 2, 29), service.findPreviousComparableDate(LocalDate.of(2024, 3, 1)));
    }
}
