package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.pojo.entity.MemoEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class MemoControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MemoController memoController;

    @Autowired
    private IMemoMapper memoMapper;

    @Test
    void testQuery_查询备忘录列表() {
        Long memoId = System.currentTimeMillis() % 1000000 + 900000L;
        memoMapper.insert(createMemo(memoId));

        CommonQuery<MemoEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new MemoEntity());

        var response = memoController.query(ApiRequestFixtures.query(query, top.aiolife.record.pojo.query.MemoQuery.class));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    private MemoEntity createMemo(Long memoId) {
        MemoEntity entity = new MemoEntity();
        entity.setId(memoId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试备忘录");
        entity.setContent("测试内容");
        return entity;
    }
}
