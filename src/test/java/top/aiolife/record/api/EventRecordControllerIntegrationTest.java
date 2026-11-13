package top.aiolife.record.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.record.mapper.IEventRecordMapper;
import top.aiolife.record.pojo.entity.EventRecordEntity;
import top.aiolife.record.pojo.req.EventRecordCreateReq;
import top.aiolife.record.pojo.req.EventRecordUpdateReq;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventRecordController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class EventRecordControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventRecordController eventRecordController;

    @Autowired
    private IEventRecordMapper eventRecordMapper;

    @Test
    void testQuery_查询活动记录() {
        Long recordId = System.currentTimeMillis() % 1000000 + 900000L;
        eventRecordMapper.insert(createEventRecord(recordId));

        var response = eventRecordController.queryEventRecords(String.valueOf(TEST_USER_ID), 1, 10);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testCreate_新增活动记录() {
        EventRecordEntity entity = createEventRecord(null);
        var response = eventRecordController.createEventRecord(String.valueOf(TEST_USER_ID), ApiRequestFixtures.request(entity, EventRecordCreateReq.class));
        var created = assertSuccessWithData(response);
        assertNotNull(created.getId());

        EventRecordEntity dbEntity = eventRecordMapper.selectById(created.getId());
        assertNotNull(dbEntity);
        assertEquals(TEST_USER_ID, dbEntity.getUserId());
        assertEquals(TEST_USER_ID, dbEntity.getCreateUser());
        assertEquals(TEST_USER_ID, dbEntity.getUpdateUser());
        assertNotNull(dbEntity.getCreateTime());
        assertNotNull(dbEntity.getUpdateTime());
        assertEquals(0, dbEntity.getIsDeleted());
    }

    @Test
    void testUpdate_更新活动记录() {
        Long recordId = System.currentTimeMillis() % 1000000 + 900000L;
        eventRecordMapper.insert(createEventRecord(recordId));

        EventRecordEntity updateParam = new EventRecordEntity();
        updateParam.setId(recordId);
        updateParam.setTitle("改名后的活动");
        updateParam.setCity("上海");
        var response = eventRecordController.updateEventRecord(String.valueOf(TEST_USER_ID), ApiRequestFixtures.request(updateParam, EventRecordUpdateReq.class));
        assertSuccess(response);

        EventRecordEntity dbEntity = eventRecordMapper.selectById(recordId);
        assertEquals("改名后的活动", dbEntity.getTitle());
        assertEquals("上海", dbEntity.getCity());
        // 更新不应覆盖创建人
        assertEquals(TEST_USER_ID, dbEntity.getUserId());
        assertEquals(TEST_USER_ID, dbEntity.getCreateUser());
    }

    @Test
    void testDelete_删除活动记录() {
        Long recordId = System.currentTimeMillis() % 1000000 + 900000L;
        eventRecordMapper.insert(createEventRecord(recordId));

        var response = eventRecordController.deleteEventRecord(String.valueOf(TEST_USER_ID), String.valueOf(recordId));
        assertSuccess(response);
        assertTrue(response.getData());

        // 逻辑删除后查不到
        assertNull(eventRecordMapper.selectById(recordId));
    }

    @Test
    void testOwnership_代录记录由所属用户查询修改删除且保留创建审计() {
        EventRecordEntity owned = createEventRecord(null);
        owned.setCreateUser(2L);
        // 固定小数秒，覆盖 PostgreSQL DATETIME 舍入到下一秒的场景。
        owned.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5, 750_000_000));
        eventRecordMapper.insert(owned);
        // 创建审计是否被更新应比较持久化前后值，而非截断小数秒的内存值。
        LocalDateTime persistedCreateTime = eventRecordMapper.selectById(owned.getId()).getCreateTime();
        assertNotNull(persistedCreateTime);
        EventRecordEntity other = createEventRecord(null);
        other.setUserId(2L); // 当前用户创建，但属于别人。
        eventRecordMapper.insert(other);

        var records = assertSuccessWithData(eventRecordController.queryEventRecords(String.valueOf(TEST_USER_ID), 1, 100)).getItems();
        assertTrue(records.stream().anyMatch(record -> owned.getId().equals(record.getId())));
        assertFalse(records.stream().anyMatch(record -> other.getId().equals(record.getId())));

        EventRecordUpdateReq update = new EventRecordUpdateReq();
        update.setId(owned.getId());
        update.setTitle("所属用户编辑");
        assertSuccess(eventRecordController.updateEventRecord(String.valueOf(TEST_USER_ID), update));
        EventRecordEntity stored = eventRecordMapper.selectById(owned.getId());
        assertEquals(TEST_USER_ID, stored.getUserId());
        assertEquals(2L, stored.getCreateUser());
        assertEquals(persistedCreateTime, stored.getCreateTime());
        assertEquals(TEST_USER_ID, stored.getUpdateUser());
        assertEquals("所属用户编辑", stored.getTitle());

        update.setId(other.getId());
        assertThrows(IllegalArgumentException.class, () -> eventRecordController.updateEventRecord(String.valueOf(TEST_USER_ID), update));
        assertFalse(assertSuccessWithData(eventRecordController.deleteEventRecord(String.valueOf(TEST_USER_ID), String.valueOf(other.getId()))));
        assertEquals("测试活动", eventRecordMapper.selectById(other.getId()).getTitle());
        assertTrue(assertSuccessWithData(eventRecordController.deleteEventRecord(String.valueOf(TEST_USER_ID), String.valueOf(owned.getId()))));
        assertNull(eventRecordMapper.selectById(owned.getId()));
    }

    private EventRecordEntity createEventRecord(Long recordId) {
        EventRecordEntity entity = new EventRecordEntity();
        entity.setId(recordId);
        entity.fillCreateCommonField(TEST_USER_ID);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试活动");
        entity.setParticipants("测试主讲人");
        entity.setEventType("演唱会");
        entity.setEventDate(LocalDate.now());
        entity.setCity("北京");
        entity.setVenue("国家大剧院");
        entity.setTicketPrice(BigDecimal.valueOf(100));
        return entity;
    }
}
