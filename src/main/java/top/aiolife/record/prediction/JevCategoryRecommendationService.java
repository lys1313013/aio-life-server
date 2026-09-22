package top.aiolife.record.prediction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 最小上下文：当前用户在目标时刻以前的记录，以及最近同类日的完整作息。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JevCategoryRecommendationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JevCategoryClient client;
    private final ITimeRecordMapper mapper;
    private final ITimeTrackerCategoryService categories;
    private final RecommendationDataCache dataCache;

    public Long recommend(long userId, LocalDate date, int minute, boolean workday, LocalDate reference) {
        if (!client.isAvailable()) {
            return skip(userId, date, minute, client.unavailableReason());
        }
        var input = JSON.createObjectNode();
        input.putObject("target").put("date", date.toString()).put("minute", minute);
        var criteria = input.putObject("categories");
        dataCache.get("visibleCategories", () -> categories.listUserVisibleCategories(userId), userId).forEach(category -> {
            if (category.getId() != null && category.getName() != null)
                criteria.put(category.getId().toString(), category.getName());
        });
        if (criteria.isEmpty()) return skip(userId, date, minute, "NO_CATEGORIES");

        LocalDateTime now = LocalDateTime.now();
        List<LocalDate> dates = new ArrayList<>(List.of(date));
        if (reference != null) dates.add(reference);
        var query = new LambdaQueryWrapper<TimeRecordEntity>()
                .select(TimeRecordEntity::getDate, TimeRecordEntity::getStartTime,
                        TimeRecordEntity::getEndTime, TimeRecordEntity::getCategoryId)
                .eq(TimeRecordEntity::getUserId, userId)
                .in(TimeRecordEntity::getDate, dates)
                .le(TimeRecordEntity::getDate, now.toLocalDate())
                .and(q -> q.lt(TimeRecordEntity::getDate, date)
                        .or(t -> t.eq(TimeRecordEntity::getDate, date).lt(TimeRecordEntity::getEndTime, minute)))
                .and(q -> q.lt(TimeRecordEntity::getDate, now.toLocalDate())
                        .or(t -> t.eq(TimeRecordEntity::getDate, now.toLocalDate())
                                .lt(TimeRecordEntity::getEndTime, now.getHour() * 60 + now.getMinute())))
                .orderByAsc(TimeRecordEntity::getDate, TimeRecordEntity::getStartTime)
                .last("LIMIT 2881");
        var records = input.putArray("records");
        var history = dataCache.get("jevHistory", () -> mapper.selectList(query),
                userId, date, minute, reference, now.toLocalDate(), now.getHour() * 60 + now.getMinute());
        // 每日最多 1440 个有效分钟块；异常数据不截断后交给模型误判。
        if (history.size() > 2880) return skip(userId, date, minute, "HISTORY_LIMIT_EXCEEDED");
        history.forEach(record -> {
            if (record.getDate() == null || record.getStartTime() == null || record.getEndTime() == null
                    || record.getCategoryId() == null || !criteria.has(record.getCategoryId().toString())
                    || record.getStartTime() < 0 || record.getEndTime() > 1439
                    || record.getStartTime() > record.getEndTime()) return;
            if (record.getDate().isAfter(now.toLocalDate())
                    || (record.getDate().equals(now.toLocalDate())
                        && record.getEndTime() >= now.getHour() * 60 + now.getMinute())) return;
            if (!record.getDate().equals(date) && !record.getDate().equals(reference)) return;
            if (record.getDate().equals(date) && record.getEndTime() >= minute) return;
            records.addObject().put("date", record.getDate().toString())
                    .put("startMinute", record.getStartTime()).put("endMinute", record.getEndTime())
                    .put("categoryId", record.getCategoryId().toString());
        });
        if (records.isEmpty()) return skip(userId, date, minute, "NO_VALID_HISTORY");
        log.info("Jev category context: userId={}, date={}, minute={}, isWorkday={}, referenceDate={}, "
                        + "categoryCount={}, historyCount={}, validRecordCount={}",
                userId, date, minute, workday, reference, criteria.size(), history.size(), records.size());
        // 每次调用 Jev，不缓存或复用模型结果。
        return client.predict(JevCategoryProtocol.request(input, workday, reference));
    }

    private Long skip(long userId, LocalDate date, int minute, String reason) {
        log.info("Jev category skipped: userId={}, date={}, minute={}, reason={}", userId, date, minute, reason);
        return null;
    }
}
