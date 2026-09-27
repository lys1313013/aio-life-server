package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.dto.ExerciseStatisticsDTO;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.ExerciseDashboardDayVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardItemVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardSummaryVO;
import top.aiolife.record.pojo.vo.ExerciseDashboardTrendPointVO;
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
        // lastDate 是上一页最早日期的前一天，包含游标当天，避免连续日期被跳过。
        LocalDate cursor = lastDate != null ? lastDate : LocalDate.now();
        List<LocalDate> dates = baseMapper.selectDashboardDates(userId, cursor, limit + 1);
        boolean hasMore = dates.size() > limit;
        if (hasMore) {
            dates = dates.subList(0, limit);
        }
        // 先按日期分页，再完整聚合整天的数据，避免原始记录 LIMIT 截断同一天。
        Map<LocalDate, Map<Long, Integer>> groupedByDate = new LinkedHashMap<>();
        if (!dates.isEmpty()) {
            for (ExerciseStatisticsDTO record : baseMapper.selectDashboardTotals(userId, dates)) {
                groupedByDate.computeIfAbsent(record.getExerciseDate(), k -> new LinkedHashMap<>())
                        .put(record.getExerciseTypeId(), record.getExerciseCount());
            }
        }

        // 收集需要查字典的运动类型 id
        List<Long> typeIds = dates.stream()
                .flatMap(d -> groupedByDate.get(d).keySet().stream())
                .distinct()
                .toList();
        Map<Long, UserDictDataEntity> dictMap = lookupDictMap(userId, typeIds);

        // 构建同类型运动日历史，用于增减对比和最近五次趋势
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
                attachPrevDelta(item, date, count, prevHistoryByType.get(typeId));
                List<ExerciseDashboardTrendPointVO> trend = new ArrayList<>(5);
                prevHistoryByType.get(typeId).tailMap(date, true).entrySet().stream().limit(5)
                        .forEach(point -> trend.add(new ExerciseDashboardTrendPointVO(point.getKey(), point.getValue())));
                java.util.Collections.reverse(trend);
                item.setTrend(trend);
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
     * 为页面中出现的运动类型构建历史次数索引：
     * - 页面日期之前的记录：从 DB 单次查询后按 (typeId, date) 聚合
     * - 页面内更早日期的记录：直接复用已聚合的 groupedByDate（用于在同页靠前日期的 prev）
     * 返回的 Map 按日期降序排列，便于通过 tailMap(date, false) 取到「严格小于当前行日期」的最新一条
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

        // 每个类型取四个完整的历史运动日，稀疏记录也能跨页补足趋势。
        List<ExerciseStatisticsDTO> historyRecords = baseMapper.selectDashboardHistory(userId, typeIds, oldestPageDate);
        for (ExerciseStatisticsDTO r : historyRecords) {
            result.computeIfAbsent(r.getExerciseTypeId(), k -> new TreeMap<>(Comparator.reverseOrder()))
                    .put(r.getExerciseDate(), r.getExerciseCount());
        }

        // 并入页面内的聚合数据，让较新的记录引用同页更早的运动日。
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
     * 填写上一次运动的次数、差值和差值百分比
     */
    private void attachPrevDelta(
            ExerciseDashboardItemVO item,
            LocalDate date,
            int count,
            NavigableMap<LocalDate, Integer> history) {
        if (history == null) {
            return;
        }
        // 严格小于当前行日期的最近一条；history 自身按日期降序，tailMap(..., false).firstEntry() 即为目标
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
        // 上次为零时仅返回绝对增减，不计算百分比。
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
