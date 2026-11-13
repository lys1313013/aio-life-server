package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IHonorRecordMapper;
import top.aiolife.record.pojo.entity.HonorRecordEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HonorRecordController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class HonorRecordControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private HonorRecordController honorRecordController;

    @Autowired
    private IHonorRecordMapper honorRecordMapper;

    @Test
    void testQueryHonorRecords_查询荣誉列表() {
        Long honorId = System.currentTimeMillis() % 1000000 + 900000L;
        honorRecordMapper.insert(createHonorRecord(honorId));

        var response = honorRecordController.queryHonorRecords(String.valueOf(TEST_USER_ID));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().size() >= 1);
    }

    @Test
    void testGetHonorRecord_根据ID查询荣誉() {
        Long honorId = System.currentTimeMillis() % 1000000 + 900000L;
        honorRecordMapper.insert(createHonorRecord(honorId));

        var response = honorRecordController.getHonorRecord(String.valueOf(TEST_USER_ID), String.valueOf(honorId));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertEquals(honorId, response.getData().getId());
    }

    private HonorRecordEntity createHonorRecord(Long honorId) {
        HonorRecordEntity entity = new HonorRecordEntity();
        entity.setId(honorId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试荣誉");
        entity.setHonorDate(LocalDate.now());
        entity.setIsDeleted(0);
        entity.setIsTop(0);
        entity.setIsPublic(1);
        entity.fillCreateCommonField(TEST_USER_ID);
        return entity;
    }
}
