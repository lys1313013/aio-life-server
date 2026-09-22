package top.aiolife.record.prediction;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.aiolife.system.mapper.IWorkCalendarMapper;
import top.aiolife.system.service.IWorkCalendarService;
import top.aiolife.system.service.impl.WorkCalendarServiceImpl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用仓库年度日历驱动真实日历 Service；仅模拟数据库和 Redis，不按星期推导。 */
final class JevCalendarFixture {
    static IWorkCalendarService calendar() {
        TreeMap<LocalDate, Integer> days = new TreeMap<>();
        try {
            var matches = Pattern.compile("\\('([0-9-]+)', ([0-3]),").matcher(Files.readString(
                    Path.of("sql/2_ini_data/2026-09-22_sys_work_calendar_2026.sql")));
            while (matches.find()) days.put(LocalDate.parse(matches.group(1)), Integer.parseInt(matches.group(2)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (days.size() != 365) throw new IllegalStateException("2026 年日历测试数据不完整");
        var mapper = mock(IWorkCalendarMapper.class);
        when(mapper.selectDayType(any())).thenAnswer(inv -> days.get(inv.getArgument(0)));
        when(mapper.selectPreviousComparableDate(any(), anyBoolean())).thenAnswer(inv ->
                days.headMap(inv.getArgument(0), false).descendingMap().entrySet().stream()
                        .filter(e -> (e.getValue() == 0 || e.getValue() == 3) == (boolean) inv.getArgument(1))
                        .map(Map.Entry::getKey).findFirst().orElse(null));
        when(mapper.countDatesBetween(any(), any())).thenAnswer(inv ->
                (long) days.subMap(inv.getArgument(0), true, inv.getArgument(1), false).size());
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return new WorkCalendarServiceImpl(mapper, redis);
    }
}
