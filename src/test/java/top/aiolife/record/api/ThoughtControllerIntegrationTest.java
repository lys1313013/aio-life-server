package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.pojo.entity.ThoughtEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ThoughtController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class ThoughtControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ThoughtController thoughtController;

    @Autowired
    private IThoughtMapper thoughtMapper;

    @Test
    void testQuery_查询闪念列表() {
        Long thoughtId = System.currentTimeMillis() % 1000000 + 900000L;
        thoughtMapper.insert(createThought(thoughtId));

        CommonQuery<ThoughtEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new ThoughtEntity());

        var response = thoughtController.query(ApiRequestFixtures.query(query, top.aiolife.record.pojo.query.ThoughtQuery.class));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testDashboard_获取看板闪念() {
        var response = thoughtController.dashboardThoughts();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private ThoughtEntity createThought(Long thoughtId) {
        ThoughtEntity entity = new ThoughtEntity();
        entity.setId(thoughtId);
        entity.setUserId(TEST_USER_ID);
        entity.setContent("测试闪念");
        entity.setIsPinned(0);
        entity.setCreateUser(TEST_USER_ID);
        return entity;
    }
}
