package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.ExerciseDashboardDayVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardItemVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardSummaryVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.UserDictDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 运动记录Service实现类
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
@Service
public class ExerciseRecordServiceImpl extends ServiceImpl<IExerciseRecordMapper, ExerciseRecordEntity> implements IExerciseRecordService {

    private final UserDictDataService userDictDataService;

    public ExerciseRecordServiceImpl(UserDictDataService userDictDataService) {
        this.userDictDataService = userDictDataService;
    }

    @Override
    public int countTodayExerciseTypes(Long userId) {
        LocalDate today = LocalDate.now();
        return (int) this.lambdaQuery()
                .eq(ExerciseRecordEntity::getUserId, userId)
                .eq(ExerciseRecordEntity::getExerciseDate, today)
                .select(ExerciseRecordEntity::getExerciseTypeId)
                .list()
                .stream()
                .map(ExerciseRecordEntity::getExerciseTypeId)
                .distinct()
                .count();
    }

    @Override
    public int getConsecutiveExerciseDays(Long userId) {
        // 获取该用户所有不重复的运动日期，按日期降序排列
        List<LocalDate> dates = this.lambdaQuery()
                .eq(ExerciseRecordEntity::getUserId, userId)
                .select(ExerciseRecordEntity::getExerciseDate)
                .orderByDesc(ExerciseRecordEntity::getExerciseDate)
                .list()
                .stream()
                .map(ExerciseRecordEntity::getExerciseDate)
                .distinct()
                .toList();

        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate lastDate = dates.get(0);

        // 如果最后一次运动不是今天也不是昨天，则连续天数为0
        if (!lastDate.equals(today) && !lastDate.equals(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        LocalDate expectedDate = lastDate;
        for (LocalDate date : dates) {
            if (date.equals(expectedDate)) {
                streak++;
                expectedDate = expectedDate.minusDays(1);
            } else {
                break;
            }
        }

        return streak;
    }

    @Override
    public ExerciseDashboardSummaryVO getDashboardSummary(Long userId, LocalDate lastDate, int limit) {
        // 游标从传入的 lastDate 开始往前取；首次调用传 null 表示从今天（含）开始
        LocalDate cursor = lastDate != null ? lastDate : LocalDate.now().plusDays(1);

        // 一次拉够 limit 个日期对应的原始记录，再在内存里按天/类型聚合
        // 单用户单类型的运动量有限，limit 通常为 7，单页 SQL 上限取 7 * 5 = 35 条足以覆盖
        int fetchLimit = Math.max(limit * 5, 50);
        List<ExerciseRecordEntity> records = this.lambdaQuery()
                .eq(ExerciseRecordEntity::getUserId, userId)
                .lt(ExerciseRecordEntity::getExerciseDate, cursor)
                .orderByDesc(ExerciseRecordEntity::getExerciseDate,
                        ExerciseRecordEntity::getCreateTime)
                .last("LIMIT " + fetchLimit)
                .list();

        // 按日期降序聚合 → 按运动类型聚合（运动量求和）
        Map<LocalDate, Map<Long, Integer>> groupedByDate = new LinkedHashMap<>();
        for (ExerciseRecordEntity record : records) {
            LocalDate date = record.getExerciseDate();
            Long typeId = record.getExerciseTypeId();
            if (date == null || typeId == null) {
                continue;
            }
            int count = record.getExerciseCount() == null ? 0 : record.getExerciseCount();
            groupedByDate.computeIfAbsent(date, k -> new LinkedHashMap<>())
                    .merge(typeId, count, Integer::sum);
        }

        // 限制返回 limit 个日期（按日期降序）
        List<LocalDate> dates = new ArrayList<>(groupedByDate.keySet());
        dates.sort(Comparator.reverseOrder());
        boolean hasMore = dates.size() > limit;
        if (hasMore) {
            dates = dates.subList(0, limit);
        }

        // 收集需要查字典的运动类型 id
        List<Long> typeIds = dates.stream()
                .flatMap(d -> groupedByDate.get(d).keySet().stream())
                .distinct()
                .toList();
        Map<Long, UserDictDataEntity> dictMap = lookupDictMap(userId, typeIds);

        // 计算每个类型上一次（最近一次）运动的次数，用于与本次比较
        Map<Long, NavigableMap<LocalDate, Integer>> prevHistoryByType = buildPrevHistory(
                userId, typeIds, dates, groupedByDate);

        ExerciseDashboardSummaryVO result = new ExerciseDashboardSummaryVO();
        List<ExerciseDashboardDayVO> dayList = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            ExerciseDashboardDayVO dayVO = new ExerciseDashboardDayVO();
            dayVO.setDate(date);
            Map<Long, Integer> typeMap = groupedByDate.get(date);
            List<ExerciseDashboardItemVO> items = new ArrayList<>(typeMap.size());
            int total = 0;
            for (Map.Entry<Long, Integer> entry : typeMap.entrySet()) {
                ExerciseDashboardItemVO item = new ExerciseDashboardItemVO();
                Long typeId = entry.getKey();
                int count = entry.getValue();
                item.setExerciseTypeId(typeId);
                item.setCount(count);
                total += count;
                UserDictDataEntity dict = dictMap.get(typeId);
                if (dict != null) {
                    item.setTypeLabel(dict.getDictLabel());
                    item.setIcon(dict.getIcon());
                    item.setColor(dict.getColor());
                } else {
                    item.setTypeLabel("其他");
                }
                attachPrevDelta(item, typeId, date, count, prevHistoryByType.get(typeId));
                items.add(item);
            }
            // 子项按 count 降序展示
            items.sort(Comparator.comparingInt(ExerciseDashboardItemVO::getCount).reversed());
            dayVO.setItems(items);
            dayVO.setTotalCount(total);
            dayList.add(dayVO);
        }
        result.setDays(dayList);
        result.setHasMore(hasMore);
        result.setLastDate(hasMore ? dates.get(dates.size() - 1).minusDays(1) : null);
        return result;
    }

    /**
     * 为页面中出现的运动类型构建「更早一次运动」的次数索引：
     * - 页面日期之前的记录：从 DB 单次查询后按 (typeId, date) 聚合
     * - 页面内更早日期的记录：直接复用已聚合的 groupedByDate（用于在同页靠前日期的 prev）
     * 返回的 Map 按日期降序排列，便于通过 tailMap(date, false) 取到「严格小于 chip 日期」的最新一条
     */
    private Map<Long, NavigableMap<LocalDate, Integer>> buildPrevHistory(
            Long userId,
            List<Long> typeIds,
            List<LocalDate> pageDates,
            Map<LocalDate, Map<Long, Integer>> groupedByDate) {
        Map<Long, NavigableMap<LocalDate, Integer>> result = new HashMap<>();
        if (typeIds.isEmpty() || pageDates.isEmpty()) {
            return result;
        }
        LocalDate oldestPageDate = pageDates.get(pageDates.size() - 1);

        // 1. 页面最早日期之前的同类型记录（limit 500 兜底，避免深翻历史拉爆）
        List<ExerciseRecordEntity> historyRecords = this.lambdaQuery()
                .eq(ExerciseRecordEntity::getUserId, userId)
                .in(ExerciseRecordEntity::getExerciseTypeId, typeIds)
                .lt(ExerciseRecordEntity::getExerciseDate, oldestPageDate)
                .orderByDesc(ExerciseRecordEntity::getExerciseDate, ExerciseRecordEntity::getCreateTime)
                .last("LIMIT 500")
                .list();
        for (ExerciseRecordEntity r : historyRecords) {
            if (r.getExerciseDate() == null || r.getExerciseTypeId() == null) continue;
            int c = r.getExerciseCount() == null ? 0 : r.getExerciseCount();
            result.computeIfAbsent(r.getExerciseTypeId(), k -> new TreeMap<>(Comparator.reverseOrder()))
                    .merge(r.getExerciseDate(), c, Integer::sum);
        }

        // 2. 页面内所有日期的聚合数据也并入，让靠后 chip 可以引用同页靠前日期作为 prev
        for (LocalDate date : pageDates) {
            Map<Long, Integer> typeMap = groupedByDate.get(date);
            if (typeMap == null) {
                continue;
            }
            for (Map.Entry<Long, Integer> e : typeMap.entrySet()) {
                result.computeIfAbsent(e.getKey(), k -> new TreeMap<>(Comparator.reverseOrder()))
                        .merge(date, e.getValue(), Integer::sum);
            }
        }
        return result;
    }

    /**
     * 把上一次运动的次数、差值、差值百分比填到 chip 上
     */
    private void attachPrevDelta(
            ExerciseDashboardItemVO item,
            Long typeId,
            LocalDate date,
            int count,
            NavigableMap<LocalDate, Integer> history) {
        if (history == null) {
            return;
        }
        // 严格小于 chip 日期的最近一条；history 自身按日期降序，tailMap(..., false).firstEntry() 即为目标
        NavigableMap<LocalDate, Integer> tail = history.tailMap(date, false);
        if (tail.isEmpty()) {
            return;
        }
        Map.Entry<LocalDate, Integer> earliest = tail.firstEntry();
        int prevCount = earliest.getValue() == null ? 0 : earliest.getValue();
        item.setPrevDate(earliest.getKey());
        item.setPrevCount(prevCount);
        item.setDeltaCount(count - prevCount);
        if (prevCount > 0) {
            item.setDeltaPercent((int) Math.round((count - prevCount) * 100.0 / prevCount));
        }
        // prevCount == 0 时不计算百分比，前端用 prevCount==0 + deltaCount>0 渲染「新增」
    }

    private Map<Long, UserDictDataEntity> lookupDictMap(Long userId, List<Long> typeIds) {
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        List<UserDictDataEntity> dicts = userDictDataService.listUserVisibleDictData(userId, DictTypeEnum.EXERCISE_TYPE.getValue());
        Map<Long, UserDictDataEntity> map = new java.util.HashMap<>(dicts.size());
        for (UserDictDataEntity dict : dicts) {
            if (dict.getId() != null) {
                map.put(dict.getId(), dict);
            }
        }
        return map;
    }
}