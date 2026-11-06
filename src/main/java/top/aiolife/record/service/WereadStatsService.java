package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.record.pojo.vo.WereadStatsVO;

/** 首页阅读统计：把微信读书按日/按月统计转换为周、月、年柱图并计算同期变化。 */
@Service
@RequiredArgsConstructor
public class WereadStatsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> GRANULARITIES = Set.of("week", "month", "year");
    private static final Map<String, String> MODES = Map.of(
            "week", "weekly", "month", "monthly", "year", "annually");
    private static final String[] WEEK_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int REFRESH_INTERVAL = 600;

    private final IWereadService weread;
    private final HomeSectionAccessGuard access;

    public WereadStatsVO stats(long userId, String granularity) {
        return stats(userId, granularity, LocalDate.now(BUSINESS_ZONE));
    }

    WereadStatsVO stats(long userId, String granularity, LocalDate today) {
        if (granularity == null || !GRANULARITIES.contains(granularity)) {
            throw new IllegalArgumentException("统计粒度仅支持 week、month、year");
        }
        access.check(userId, "/record/weread");
        LocalDate start = start(granularity, today);
        LocalDate end = end(granularity, start);
        int elapsedDays = elapsedDays(granularity, today);
        LocalDate previousStart = previousStart(granularity, start);
        LocalDate previousEnd = previousEnd(granularity, today, previousStart);

        JsonNode current = weread.statsForUser(userId, MODES.get(granularity), epoch(start));
        if (current == null) {
            return new WereadStatsVO(false, granularity, start.toString(), end.toString(), elapsedDays,
                    List.of(), 0, 0, previousStart.toString(), previousEnd.toString(), 0, REFRESH_INTERVAL);
        }
        Map<LocalDate, Integer> currentSeconds = readSeconds(current);
        List<WereadStatsVO.Bucket> buckets = buckets(granularity, start, end, currentSeconds);
        int totalSeconds = buckets.stream().mapToInt(WereadStatsVO.Bucket::seconds).sum();
        int dailyAverage = (int) Math.round(totalSeconds / (double) elapsedDays);

        JsonNode previous = weread.statsForUser(userId, MODES.get(granularity), epoch(previousStart));
        int previousTotal = previous == null ? 0
                : sumBetween(readSeconds(previous), previousStart, previousEnd);
        return new WereadStatsVO(true, granularity, start.toString(), end.toString(), elapsedDays, buckets,
                totalSeconds, dailyAverage, previousStart.toString(), previousEnd.toString(), previousTotal,
                REFRESH_INTERVAL);
    }

    private static LocalDate start(String granularity, LocalDate today) {
        return switch (granularity) {
            case "week" -> today.with(DayOfWeek.MONDAY);
            case "month" -> today.withDayOfMonth(1);
            default -> today.withDayOfYear(1);
        };
    }

    private static LocalDate end(String granularity, LocalDate start) {
        return switch (granularity) {
            case "week" -> start.plusDays(6);
            case "month" -> start.plusMonths(1).minusDays(1);
            default -> start.plusYears(1).minusDays(1);
        };
    }

    private static int elapsedDays(String granularity, LocalDate today) {
        return switch (granularity) {
            case "week" -> today.getDayOfWeek().getValue();
            case "month" -> today.getDayOfMonth();
            default -> today.getDayOfYear();
        };
    }

    private static LocalDate previousStart(String granularity, LocalDate start) {
        return switch (granularity) {
            case "week" -> start.minusWeeks(1);
            case "month" -> start.minusMonths(1);
            default -> start.minusYears(1);
        };
    }

    /** 周、月按相同的星期/日期对齐；年统计只有月粒度，对比到上年同月月末。 */
    private static LocalDate previousEnd(String granularity, LocalDate today, LocalDate previousStart) {
        return switch (granularity) {
            case "week" -> previousStart.plusDays(today.getDayOfWeek().getValue() - 1L);
            case "month" -> {
                LocalDate previousMonthEnd = previousStart.plusMonths(1).minusDays(1);
                LocalDate sameDay = previousStart.plusDays(today.getDayOfMonth() - 1L);
                yield sameDay.isAfter(previousMonthEnd) ? previousMonthEnd : sameDay;
            }
            default -> previousStart.plusMonths(today.getMonthValue()).minusDays(1);
        };
    }

    private static long epoch(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toEpochSecond();
    }

    private static List<WereadStatsVO.Bucket> buckets(String granularity, LocalDate start, LocalDate end,
            Map<LocalDate, Integer> seconds) {
        return switch (granularity) {
            case "week" -> IntStream.range(0, 7).mapToObj(index -> {
                LocalDate date = start.plusDays(index);
                return new WereadStatsVO.Bucket(date.toString(), WEEK_LABELS[index],
                        seconds.getOrDefault(date, 0));
            }).toList();
            case "month" -> IntStream.rangeClosed(1, end.getDayOfMonth()).mapToObj(day -> {
                LocalDate date = start.withDayOfMonth(day);
                return new WereadStatsVO.Bucket(date.toString(), String.valueOf(day),
                        seconds.getOrDefault(date, 0));
            }).toList();
            default -> IntStream.rangeClosed(1, 12).mapToObj(month -> {
                int monthSeconds = 0;
                for (Map.Entry<LocalDate, Integer> entry : seconds.entrySet()) {
                    LocalDate date = entry.getKey();
                    if (date.getYear() == start.getYear() && date.getMonthValue() == month) {
                        monthSeconds = add(monthSeconds, entry.getValue());
                    }
                }
                return new WereadStatsVO.Bucket(String.format("%04d-%02d", start.getYear(), month),
                        month + "月", monthSeconds);
            }).toList();
        };
    }

    private static Map<LocalDate, Integer> readSeconds(JsonNode data) {
        Map<LocalDate, Integer> result = new HashMap<>();
        JsonNode readTimes = data.path("readTimes");
        if (!readTimes.isObject()) return result;
        Iterator<Map.Entry<String, JsonNode>> fields = readTimes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                long timestamp = Long.parseLong(entry.getKey());
                JsonNode value = entry.getValue();
                if (timestamp <= 0 || !value.isNumber()) continue;
                int seconds = value.asInt();
                if (seconds <= 0) continue;
                LocalDate date = Instant.ofEpochSecond(timestamp).atZone(BUSINESS_ZONE).toLocalDate();
                result.merge(date, seconds, WereadStatsService::add);
            } catch (NumberFormatException ignored) {
                // 非时间戳键不参与统计，避免单个异常键破坏整卡数据。
            }
        }
        return result;
    }

    private static int sumBetween(Map<LocalDate, Integer> seconds, LocalDate start, LocalDate end) {
        int total = 0;
        for (Map.Entry<LocalDate, Integer> entry : seconds.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(start) && !date.isAfter(end)) total = add(total, entry.getValue());
        }
        return total;
    }

    private static int add(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }
}
