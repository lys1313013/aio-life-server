package top.aiolife.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.aiolife.system.mapper.IWorkCalendarMapper;
import top.aiolife.system.service.IWorkCalendarService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 工作日历查询，仅将“目标日期到上一个同类日”的成功结果按天缓存 2 天。 */
@Service
@RequiredArgsConstructor
public class WorkCalendarServiceImpl implements IWorkCalendarService {
    private static final String CACHE_PREFIX = "workCalendar:prevDate:";
    private static final Duration CACHE_TTL = Duration.ofDays(2);

    private final IWorkCalendarMapper workCalendarMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Boolean isWorkday(LocalDate date) {
        return classify(workCalendarMapper.selectDayType(date));
    }

    @Override
    public LocalDate findPreviousComparableDate(LocalDate date) {
        String key = CACHE_PREFIX + date;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return LocalDate.parse(cached);
        }
        Boolean workday = isWorkday(date);
        if (workday == null) {
            return null;
        }
        LocalDate candidate = workCalendarMapper.selectPreviousComparableDate(date, workday);
        if (candidate == null) {
            return null;
        }
        // 日历可能仅导入了部分年份，不能跨过未知日期返回一个并非最近的同类日。
        if (workCalendarMapper.countDatesBetween(candidate, date) != ChronoUnit.DAYS.between(candidate, date)) {
            return null;
        }
        redisTemplate.opsForValue().set(key, candidate.toString(), CACHE_TTL);
        return candidate;
    }

    @Override
    public void evictPreviousComparableDate(LocalDate date) {
        redisTemplate.delete(CACHE_PREFIX + date);
    }

    private Boolean classify(Integer dayType) {
        if (dayType == null) {
            return null;
        }
        return switch (dayType) {
            case 0, 3 -> true;
            case 1, 2 -> false;
            default -> throw new IllegalStateException("工作日历日期类型无效：" + dayType);
        };
    }
}
