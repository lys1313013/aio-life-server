package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IAnniversaryRecordMapper;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnniversaryRecordController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class AnniversaryRecordControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AnniversaryRecordController anniversaryRecordController;

    @Autowired
    private IAnniversaryRecordMapper anniversaryRecordMapper;

    @Test
    void testQueryAnniversaryRecords_查询纪念日列表() {
        Long anniversaryId = System.currentTimeMillis() % 1000000 + 900000L;
        anniversaryRecordMapper.insert(createAnniversaryRecord(anniversaryId));

        var response = anniversaryRecordController.queryAnniversaryRecords(null);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().size() >= 1);
    }

    @Test
    void testGetAnniversaryRecord_根据ID查询纪念日() {
        Long anniversaryId = System.currentTimeMillis() % 1000000 + 900000L;
        anniversaryRecordMapper.insert(createAnniversaryRecord(anniversaryId));

        var response = anniversaryRecordController.getAnniversaryRecord(anniversaryId);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertEquals(anniversaryId, response.getData().getId());
    }

    private AnniversaryRecordEntity createAnniversaryRecord(Long anniversaryId) {
        AnniversaryRecordEntity entity = new AnniversaryRecordEntity();
        entity.setId(anniversaryId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试纪念日");
        entity.setTargetDate(LocalDate.now());
        entity.setType("anniversary");
        entity.setIsDeleted(0);
        return entity;
    }
}
