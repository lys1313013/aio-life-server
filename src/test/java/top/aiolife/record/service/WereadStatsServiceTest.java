package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WereadStatsServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final IWereadService weread = mock(IWereadService.class);
    private final HomeSectionAccessGuard access = mock(HomeSectionAccessGuard.class);
    private final WereadStatsService service = new WereadStatsService(weread, access);

    @Test
    void testWeek_补齐七天并按已过天数计算日均和同期() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        LocalDate start = LocalDate.of(2026, 11, 2);
        LocalDate previousStart = LocalDate.of(2026, 10, 26);
        when(weread.statsForUser(1L, "weekly", epoch(start)))
                .thenReturn(data(Map.of(start, 2100, start.plusDays(1), 1800)));
        when(weread.statsForUser(1L, "weekly", epoch(previousStart)))
                .thenReturn(data(Map.of(previousStart, 600, previousStart.plusDays(4), 900)));

        var result = service.stats(1L, "week", today);

        verify(access).check(1L, "/record/weread");
        assertTrue(result.connected());
        assertEquals("week", result.granularity());
        assertEquals("2026-11-02", result.periodStart());
        assertEquals("2026-11-08", result.periodEnd());
        assertEquals(5, result.elapsedDays());
        assertEquals(7, result.buckets().size());
        assertEquals("周一", result.buckets().getFirst().label());
        assertEquals(2100, result.buckets().getFirst().seconds());
        assertEquals(0, result.buckets().get(6).seconds());
        assertEquals(3900, result.totalSeconds());
        assertEquals(780, result.dailyAverageSeconds());
        assertEquals("2026-10-26", result.previousPeriodStart());
        assertEquals("2026-10-30", result.previousPeriodEnd());
        assertEquals(1500, result.previousTotalSeconds());
        assertEquals(600, result.refreshInterval());
    }

    @Test
    void testWeek_周日对比上周完整七天() {
        LocalDate today = LocalDate.of(2026, 11, 8);
        LocalDate start = LocalDate.of(2026, 11, 2);
        when(weread.statsForUser(1L, "weekly", epoch(start))).thenReturn(data(Map.of(start, 600)));
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 10, 26))))
                .thenReturn(data(Map.of(LocalDate.of(2026, 10, 26), 300)));

        var result = service.stats(1L, "week", today);

        assertEquals(7, result.elapsedDays());
        assertEquals("2026-11-01", result.previousPeriodEnd());
    }

    @Test
    void testMonth_上月天数不足时截到月末且对比区间过滤越界日期() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate previousStart = LocalDate.of(2026, 2, 1);
        var previousMonth = data(Map.of(LocalDate.of(2026, 2, 1), 600,
                LocalDate.of(2026, 2, 28), 900,
                LocalDate.of(2026, 3, 1), 5000));
        when(weread.statsForUser(1L, "monthly", epoch(start)))
                .thenReturn(data(Map.of(start, 1200, LocalDate.of(2026, 3, 31), 2400)));
        when(weread.statsForUser(1L, "monthly", epoch(previousStart))).thenReturn(previousMonth);

        var result = service.stats(1L, "month", today);

        assertEquals(31, result.buckets().size());
        assertEquals("1", result.buckets().getFirst().label());
        assertEquals("31", result.buckets().getLast().label());
        assertEquals(3600, result.totalSeconds());
        assertEquals(116, result.dailyAverageSeconds());
        assertEquals("2026-02-28", result.previousPeriodEnd());
        assertEquals(1500, result.previousTotalSeconds());
    }

    @Test
    void testYear_按月聚合且对比到上年同月月末() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate previousStart = LocalDate.of(2025, 1, 1);
        when(weread.statsForUser(1L, "annually", epoch(start)))
                .thenReturn(data(Map.of(start, 3600, LocalDate.of(2026, 11, 1), 7200)));
        when(weread.statsForUser(1L, "annually", epoch(previousStart)))
                .thenReturn(data(Map.of(previousStart, 600, LocalDate.of(2025, 11, 1), 900,
                        LocalDate.of(2025, 12, 1), 1200)));

        var result = service.stats(1L, "year", today);

        assertEquals(12, result.buckets().size());
        assertEquals("2026-01", result.buckets().getFirst().key());
        assertEquals("1月", result.buckets().getFirst().label());
        assertEquals("2026-12", result.buckets().getLast().key());
        assertEquals(10800, result.totalSeconds());
        assertEquals(310, result.elapsedDays());
        assertEquals(35, result.dailyAverageSeconds());
        assertEquals("2025-01-01", result.previousPeriodStart());
        assertEquals("2025-11-30", result.previousPeriodEnd());
        assertEquals(1500, result.previousTotalSeconds());
    }

    @Test
    void testUnbound_返回未连接且只查询一次() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 11, 2)))).thenReturn(null);

        var result = service.stats(1L, "week", today);

        assertFalse(result.connected());
        assertEquals(0, result.totalSeconds());
        assertEquals(0, result.dailyAverageSeconds());
        assertTrue(result.buckets().isEmpty());
        assertEquals("2026-11-02", result.periodStart());
        verify(weread, times(1)).statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void testEmpty_已连接但无阅读仍返回零值完整桶() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 11, 2))))
                .thenReturn(JsonNodeFactory.instance.objectNode());
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 10, 26))))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        var result = service.stats(1L, "week", today);

        assertTrue(result.connected());
        assertEquals(0, result.totalSeconds());
        assertEquals(0, result.dailyAverageSeconds());
        assertEquals(7, result.buckets().size());
        assertTrue(result.buckets().stream().allMatch(bucket -> bucket.seconds() == 0));
    }

    @Test
    void testInvalidGranularity_不查询权限和上游() {
        assertThrows(IllegalArgumentException.class,
                () -> service.stats(1L, "day", LocalDate.of(2026, 11, 6)));

        verifyNoInteractions(access, weread);
    }

    @Test
    void testInvalidReadTimes_忽略非法键和负值() {
        LocalDate today = LocalDate.of(2026, 11, 6);
        ObjectNode current = JsonNodeFactory.instance.objectNode();
        ObjectNode readTimes = current.putObject("readTimes");
        readTimes.put("not-a-timestamp", 100);
        readTimes.put(String.valueOf(epoch(LocalDate.of(2026, 11, 2))), -10);
        readTimes.put(String.valueOf(epoch(LocalDate.of(2026, 11, 3))), 120);
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 11, 2)))).thenReturn(current);
        when(weread.statsForUser(1L, "weekly", epoch(LocalDate.of(2026, 10, 26))))
                .thenReturn(JsonNodeFactory.instance.objectNode());

        var result = service.stats(1L, "week", today);

        assertEquals(120, result.totalSeconds());
        assertEquals(120, result.buckets().get(1).seconds());
    }

    private static long epoch(LocalDate date) {
        return date.atStartOfDay(ZONE).toEpochSecond();
    }

    private static JsonNode data(Map<LocalDate, Integer> values) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode readTimes = root.putObject("readTimes");
        values.forEach((date, seconds) -> readTimes.put(String.valueOf(epoch(date)), seconds));
        return root;
    }
}
