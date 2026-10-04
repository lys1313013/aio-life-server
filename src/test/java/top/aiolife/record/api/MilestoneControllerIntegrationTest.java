package top.aiolife.record.api;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IMilestoneMapper;
import top.aiolife.record.pojo.entity.MilestoneEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MilestoneController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class MilestoneControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MilestoneController milestoneController;

    @Autowired
    private IMilestoneMapper milestoneMapper;

    @Test
    void testQueryMilestone_查询里程碑() {
        Long milestoneId = System.currentTimeMillis() % 1000000 + 900000L;
        milestoneMapper.insert(createMilestone(milestoneId));

        var response = milestoneController.queryMilestone();
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().size() >= 1);
    }

    private MilestoneEntity createMilestone(Long milestoneId) {
        MilestoneEntity entity = new MilestoneEntity();
        entity.setId(milestoneId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试里程碑");
        entity.setDate(LocalDate.now().toString());
        entity.setIsDeleted(0);
        entity.fillCreateCommonField(TEST_USER_ID);
        return entity;
    }
}
