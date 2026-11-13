package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DashboardController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class DashboardControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DashboardController dashboardController;

    @Test
    void testGetTasks_获取任务列表() {
        var response = dashboardController.getTasks(String.valueOf(TEST_USER_ID));
        assertSuccess(response);
        assertNotNull(response.getData());
    }
}
