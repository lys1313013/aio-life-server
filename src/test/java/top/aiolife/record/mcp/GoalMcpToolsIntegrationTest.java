package top.aiolife.record.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.mcp.req.GoalProgressUpdateMcpReq;
import top.aiolife.record.mcp.req.GoalQueryMcpReq;
import top.aiolife.record.mcp.vo.GoalMcpVO;
import top.aiolife.record.mcp.vo.GoalPageMcpVO;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.enums.GoalStatusEnum;
import top.aiolife.record.pojo.enums.GoalTypeEnum;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GoalMcpTools 集成测试
 *
 * @author Lys
 * @date 2026/08/30
 */
class GoalMcpToolsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GoalMcpTools goalMcpTools;

    @Autowired
    private IGoalMapper goalMapper;

    @Test
    void testGoalQuery_查询目标列表_状态与类型返回中文标签() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        goalMapper.insert(createGoal(goalId, "MCP测试目标-查询"));

        GoalQueryMcpReq req = new GoalQueryMcpReq();
        req.setKeyword("MCP测试目标-查询");
        GoalPageMcpVO result = goalMcpTools.goal_query(req);

        assertNotNull(result);
        assertTrue(result.getTotal() >= 1);
        GoalMcpVO vo = result.getRecords().stream()
                .filter(g -> goalId.equals(g.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(GoalTypeEnum.DAY.getDesc(), vo.getType());
        assertEquals(GoalStatusEnum.PENDING.getDesc(), vo.getStatus());
        assertEquals(0, vo.getTotalCount());
        assertEquals(0, vo.getCompletedCount());
    }

    @Test
    void testGoalQuery_子目标计数() {
        Long parentId = System.currentTimeMillis() % 1000000 + 900000L;
        Long childId1 = parentId + 1;
        Long childId2 = parentId + 2;
        goalMapper.insert(createGoal(parentId, "MCP父目标"));
        GoalEntity child1 = createGoal(childId1, "MCP子目标1");
        child1.setParentId(parentId);
        child1.setStatus(GoalStatusEnum.COMPLETED.getCode());
        GoalEntity child2 = createGoal(childId2, "MCP子目标2");
        child2.setParentId(parentId);
        goalMapper.insert(child1);
        goalMapper.insert(child2);

        GoalQueryMcpReq req = new GoalQueryMcpReq();
        req.setKeyword("MCP父目标");
        GoalPageMcpVO result = goalMcpTools.goal_query(req);

        GoalMcpVO vo = result.getRecords().stream()
                .filter(g -> parentId.equals(g.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, vo.getTotalCount());
        assertEquals(1, vo.getCompletedCount());
    }

    @Test
    void testGoalProgressUpdate_仅更新进度() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        GoalEntity goal = createGoal(goalId, "MCP测试目标-进度");
        goal.setTargetValue(12);
        goal.setStatus(GoalStatusEnum.IN_PROGRESS.getCode());
        goalMapper.insert(goal);

        GoalProgressUpdateMcpReq req = new GoalProgressUpdateMcpReq();
        req.setGoalId(goalId);
        req.setCurrentValue(7);
        String message = goalMcpTools.goal_progress_update(req);

        assertTrue(message.contains("进度已更新：7/12"));
        GoalEntity updated = goalMapper.selectById(goalId);
        assertEquals(7, updated.getCurrentValue());
        assertEquals(GoalStatusEnum.IN_PROGRESS.getCode(), updated.getStatus());
        assertNull(updated.getCompletedAt());
    }

    @Test
    void testGoalProgressUpdate_标记完成_自动补齐进度与完成时间() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        GoalEntity goal = createGoal(goalId, "MCP测试目标-完成");
        goal.setTargetValue(12);
        goal.setCurrentValue(7);
        goal.setStatus(GoalStatusEnum.IN_PROGRESS.getCode());
        goalMapper.insert(goal);

        GoalProgressUpdateMcpReq req = new GoalProgressUpdateMcpReq();
        req.setGoalId(goalId);
        req.setStatus("已完成");
        String message = goalMcpTools.goal_progress_update(req);

        assertTrue(message.contains("状态已变更为：已完成"));
        GoalEntity updated = goalMapper.selectById(goalId);
        assertEquals(GoalStatusEnum.COMPLETED.getCode(), updated.getStatus());
        assertEquals(12, updated.getCurrentValue());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void testGoalProgressUpdate_已完成目标不允许再流转() {
        Long goalId = System.currentTimeMillis() % 1000000 + 900000L;
        GoalEntity goal = createGoal(goalId, "MCP测试目标-已完成");
        goal.setStatus(GoalStatusEnum.COMPLETED.getCode());
        goal.setCompletedAt(LocalDateTime.now());
        goalMapper.insert(goal);

        GoalProgressUpdateMcpReq req = new GoalProgressUpdateMcpReq();
        req.setGoalId(goalId);
        req.setStatus("已放弃");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> goalMcpTools.goal_progress_update(req));
        assertTrue(ex.getMessage().contains("不允许直接变更状态"));
    }

    @Test
    void testGoalProgressUpdate_参数校验() {
        GoalProgressUpdateMcpReq req = new GoalProgressUpdateMcpReq();
        assertThrows(IllegalArgumentException.class, () -> goalMcpTools.goal_progress_update(req));

        req.setGoalId(1L);
        assertThrows(IllegalArgumentException.class, () -> goalMcpTools.goal_progress_update(req));

        req.setCurrentValue(1);
        req.setStatus("待开始");
        assertThrows(IllegalArgumentException.class, () -> goalMcpTools.goal_progress_update(req));
    }

    private GoalEntity createGoal(Long goalId, String title) {
        GoalEntity entity = new GoalEntity();
        entity.setId(goalId);
        entity.setUserId(TEST_USER_ID);
        entity.setType(GoalTypeEnum.DAY.getCode());
        entity.setTitle(title);
        entity.setStatus(GoalStatusEnum.PENDING.getCode());
        entity.setIsDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
