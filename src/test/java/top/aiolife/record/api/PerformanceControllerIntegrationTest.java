package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IPerformanceMapper;
import top.aiolife.record.pojo.entity.PerformanceEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerformanceController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class PerformanceControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PerformanceController performanceController;

    @Autowired
    private IPerformanceMapper performanceMapper;

    @Test
    void testQuery_查询演出记录() {
        Long perfId = System.currentTimeMillis() % 1000000 + 900000L;
        performanceMapper.insert(createPerformance(perfId));

        var response = performanceController.queryPerformances(1, 10);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testCreate_新增演出记录() {
        PerformanceEntity entity = createPerformance(null);
        var response = performanceController.createPerformance(entity);
        PerformanceEntity created = assertSuccessWithData(response);
        assertNotNull(created.getId());

        PerformanceEntity dbEntity = performanceMapper.selectById(created.getId());
        assertNotNull(dbEntity);
        assertEquals(TEST_USER_ID, dbEntity.getCreateUser());
        assertEquals(TEST_USER_ID, dbEntity.getUpdateUser());
        assertNotNull(dbEntity.getCreateTime());
        assertNotNull(dbEntity.getUpdateTime());
        assertEquals(0, dbEntity.getIsDeleted());
    }

    @Test
    void testUpdate_更新演出记录() {
        Long perfId = System.currentTimeMillis() % 1000000 + 900000L;
        performanceMapper.insert(createPerformance(perfId));

        PerformanceEntity updateParam = new PerformanceEntity();
        updateParam.setId(perfId);
        updateParam.setPerformanceName("改名后的演出");
        updateParam.setCity("上海");
        var response = performanceController.updatePerformance(updateParam);
        assertSuccess(response);

        PerformanceEntity dbEntity = performanceMapper.selectById(perfId);
        assertEquals("改名后的演出", dbEntity.getPerformanceName());
        assertEquals("上海", dbEntity.getCity());
        // 更新不应覆盖创建人
        assertEquals(TEST_USER_ID, dbEntity.getCreateUser());
    }

    @Test
    void testDelete_删除演出记录() {
        Long perfId = System.currentTimeMillis() % 1000000 + 900000L;
        performanceMapper.insert(createPerformance(perfId));

        var response = performanceController.deletePerformance(perfId);
        assertSuccess(response);
        assertTrue(response.getData());

        // 逻辑删除后查不到
        assertNull(performanceMapper.selectById(perfId));
    }

    private PerformanceEntity createPerformance(Long perfId) {
        PerformanceEntity entity = new PerformanceEntity();
        entity.setId(perfId);
        entity.fillCreateCommonField(TEST_USER_ID);
        entity.setPerformanceName("测试演出");
        entity.setPerformer("测试演员");
        entity.setPerformanceType("演唱会");
        entity.setPerformanceDate(java.time.LocalDate.now());
        entity.setCity("北京");
        entity.setVenue("国家大剧院");
        entity.setTicketPrice(java.math.BigDecimal.valueOf(100));
        return entity;
    }
}
