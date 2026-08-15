package top.aiolife.record.provider.impl;

import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IExerciseRecordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运动卡片提供者
 *
 * @author Lys
 * @date 2026/01/23 23:08
 */
@Slf4j
@Component
@AllArgsConstructor
public class ExerciseCardProvider implements DashboardCardProvider {

    private final IExerciseRecordService exerciseRecordService;

    @Override
    public String getType() {
        return "EXERCISE";
    }

    @Override
    public String getTitle() {
        return "今日运动";
    }

    @Override
    public String getTotalTitle() {
        return "连续运动";
    }

    @Override
    public String getIcon() {
        return "mdi:run";
    }

    @Override
    public int getOrder() {
        return 4;
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIcon(getIcon());
        card.setTitle(getTitle());
        card.setIconClickUrl("/record/exercise");
        card.setTitleClickUrl("action:open-exercise-modal");
        try {
            int count = exerciseRecordService.countTodayExerciseTypes(userId);
            card.setValue(String.valueOf(count));
            card.setValueColor(count == 0 ? "red" : "#3FB27F");
            card.setTotalTitle(getTotalTitle());
            card.setTotalValue(exerciseRecordService.getConsecutiveExerciseDays(userId) + " 天");
            card.setRefreshInterval(600);
        } catch (Exception e) {
            log.error("获取运动数据失败", e);
            card.setValue("获取失败");
            card.setTotalValue("获取失败");
        }
        return card;
    }
}
