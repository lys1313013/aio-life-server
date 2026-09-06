package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BVideoController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class BVideoControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BVideoController bVideoController;

    @Autowired
    private IBVideoMapper bVideoMapper;

    @Test
    void testQuery_查询B站视频() {
        Long videoId = System.currentTimeMillis() % 1000000 + 900000L;
        bVideoMapper.insert(createBVideo(videoId));

        CommonQuery<BVideoEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new BVideoEntity());

        var response = bVideoController.query(query);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testGetStatusCount_获取状态统计() {
        var response = bVideoController.getStatusCount();
        assertSuccess(response);
    }

    @Test
    void testStatistics_获取统计信息() {
        var response = bVideoController.statistics();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private BVideoEntity createBVideo(Long videoId) {
        BVideoEntity entity = new BVideoEntity();
        entity.setId(videoId);
        entity.setUserId(TEST_USER_ID);
        entity.setCreateUser(TEST_USER_ID);
        entity.setBvid("BV1test" + videoId);
        entity.setTitle("测试视频");
        entity.setUrl("https://www.bilibili.com/video/BV1test" + videoId);
        entity.setDuration(3600);
        entity.setStatus(ProgressStatusEnum.NOT_STARTED);
        entity.setIsDeleted(0);
        return entity;
    }
}
