package top.aiolife.record.provider.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 阅读卡片提供者
 *
 * @author Lys
 * @date 2026/07/08
 */
@Slf4j
@Component
@AllArgsConstructor
public class ReadCardProvider implements DashboardCardProvider {

    private static final String READ_CATEGORY_NAME = "阅读";

    private final ITimeTrackerCategoryService categoryService;
    private final ITimeRecordService timeRecordService;
    private final IReadRecordService readRecordService;

    @Override
    public String getType() {
        return "READ";
    }

    @Override
    public String getTitle() {
        return "今日阅读";
    }

    @Override
    public String getTotalTitle() {
        return "本月读完";
    }

    @Override
    public String getIcon() {
        return "lucide:book-open";
    }

    @Override
    public int getOrder() {
        return 6;
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIcon(getIcon());
        card.setTitle(getTitle());
        card.setTotalTitle(getTotalTitle());

        try {
            int minutes = sumTodayReadDuration(userId);
            card.setValue(formatDuration(minutes));
            card.setValueColor(minutes == 0 ? "red" : "#3FB27F");

            long finishedThisMonth = countFinishedThisMonth(userId);
            card.setTotalValue(finishedThisMonth + " 本");

            card.setRefreshInterval(300);
        } catch (Exception e) {
            log.error("获取阅读数据失败", e);
            card.setValue("获取失败");
            card.setTotalValue("获取失败");
        }
        return card;
    }

    private int sumTodayReadDuration(long userId) {
        List<Long> categoryIds = categoryService.listUserVisibleCategories(userId).stream()
                .filter(c -> READ_CATEGORY_NAME.equals(c.getName()))
                .map(c -> c.getId())
                .toList();

        if (categoryIds.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        return timeRecordService.lambdaQuery()
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, today)
                .in(TimeRecordEntity::getCategoryId, categoryIds)
                .list()
                .stream()
                .map(TimeRecordEntity::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private long countFinishedThisMonth(long userId) {
        LocalDate now = LocalDate.now();
        LocalDateTime start = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = now.atTime(LocalTime.MAX);
        return readRecordService.lambdaQuery()
                .eq(ReadRecordEntity::getUserId, userId)
                .eq(ReadRecordEntity::getStatus, ProgressStatusEnum.COMPLETED.getCode())
                .ge(ReadRecordEntity::getFinishTime, start)
                .le(ReadRecordEntity::getFinishTime, end)
                .count();
    }

    private String formatDuration(int minutes) {
        if (minutes <= 0) {
            return "0分";
        }
        if (minutes < 60) {
            return minutes + "分";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        return mins == 0 ? hours + "小时" : hours + "小时" + mins + "分";
    }
}
