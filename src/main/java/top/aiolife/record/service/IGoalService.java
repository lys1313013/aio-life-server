package top.aiolife.record.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.GoalEntity;

/**
 * 目标服务接口
 *
 * @author Lys
 * @date 2026-04-05
 */
public interface IGoalService extends IService<GoalEntity> {
    GoalEntity createForUser(long userId, GoalEntity entity);

    GoalEntity updateForUser(long userId, GoalEntity entity);

    GoalEntity setPinned(long userId, long id, int isPinned);

    void reorderPinned(long userId, List<Long> ids);

    void deleteForUser(long userId, List<String> ids);
}
