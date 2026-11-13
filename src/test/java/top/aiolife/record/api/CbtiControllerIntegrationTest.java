package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.service.ICbtiService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CbtiController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class CbtiControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CbtiController cbtiController;

    @Autowired
    private ICbtiService cbtiService;

    @Test
    void testQuestions_获取题目() {
        var response = cbtiController.questions();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testPersonalities_获取人格列表() {
        var response = cbtiController.personalities();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testResults_获取测试结果() {
        var response = cbtiController.results(String.valueOf(TEST_USER_ID));
        assertSuccess(response);
        assertNotNull(response.getData());
    }
}
