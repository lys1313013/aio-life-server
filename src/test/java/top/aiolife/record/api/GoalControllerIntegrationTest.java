package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GoalController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class GoalControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GoalController goalController;

    @Autowired
    private IGoalMapper goalMapper;

    @Test
    void testQueryGoals_查询目标列表() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        goalMapper.insert(createGoal(goalId));

        var response = goalController.queryGoals(null, null, null);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().size() >= 1);
    }

    @Test
    void testQueryGoalsByType_根据类型查询目标() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        goalMapper.insert(createGoal(goalId));

        var response = goalController.queryGoals(1, null, null);
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private GoalEntity createGoal(Long goalId) {
        GoalEntity entity = new GoalEntity();
        entity.setId(goalId);
        entity.setUserId(TEST_USER_ID);
        entity.setType(1);
        entity.setTitle("测试目标");
        entity.setStatus(ProgressStatusEnum.NOT_STARTED);
        entity.setIsDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
