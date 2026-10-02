package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.ITaskColumnMapper;
import top.aiolife.record.pojo.entity.TaskColumnEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskColumnController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class TaskColumnControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TaskColumnController taskColumnController;

    @Autowired
    private ITaskColumnMapper taskColumnMapper;

    @Test
    void testQuery_查询任务栏() {
        Long columnId = System.currentTimeMillis() % 1000000 + 900000L;
        taskColumnMapper.insert(createTaskColumn(columnId));

        CommonQuery<TaskColumnEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new TaskColumnEntity());

        var response = taskColumnController.query(ApiRequestFixtures.query(query, top.aiolife.record.pojo.query.TaskColumnQuery.class));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    private TaskColumnEntity createTaskColumn(Long columnId) {
        TaskColumnEntity entity = new TaskColumnEntity();
        entity.setId(columnId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试栏目");
        entity.setSortOrder(1);
        entity.setIsDeleted(0);
        return entity;
    }
}
