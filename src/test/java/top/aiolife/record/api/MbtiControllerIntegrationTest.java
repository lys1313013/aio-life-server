package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.service.IMbtiResultService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MbtiController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class MbtiControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MbtiController mbtiController;

    @Autowired
    private IMbtiResultService mbtiResultService;

    @Test
    void testGetHistory_获取历史记录() {
        var response = mbtiController.getHistory();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testDeleteResult_删除本人记录() {
        var entity = new MbtiResultEntity();
        entity.setUserId(TEST_USER_ID);
        entity.setTestId("test-delete-1");
        entity.setMbtiType("INTJ");
        entity.fillCreateCommonField(TEST_USER_ID);
        mbtiResultService.saveResult(entity);

        var response = mbtiController.deleteResult(entity.getId());
        assertSuccess(response);
        assertNull(mbtiResultService.getById(entity.getId()));
    }

    @Test
    void testDeleteResult_删除他人记录返回失败() {
        var entity = new MbtiResultEntity();
        entity.setUserId(999L);
        entity.setTestId("test-delete-2");
        entity.setMbtiType("ENTP");
        entity.fillCreateCommonField(999L);
        mbtiResultService.saveResult(entity);

        var response = mbtiController.deleteResult(entity.getId());
        assertEquals(ResponseCodeConst.RSCODE_COMMON_FAIL, response.getRscode());
        assertNotNull(mbtiResultService.getById(entity.getId()));
    }
}
