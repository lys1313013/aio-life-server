package top.aiolife.record.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.service.IReadRecordService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReadRecordServiceImpl 集成测试
 *
 * @author Lys
 * @date 2026/08/26
 */
class ReadRecordServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IReadRecordService readRecordService;

    @Test
    void testSaveRecord_审计字段自动填充() {
        ReadRecordReq req = new ReadRecordReq();
        req.setTitle("测试书籍");
        req.setType(1);
        req.setStatus(0);

        readRecordService.saveRecord(req);

        ReadRecordEntity saved = readRecordService.lambdaQuery()
                .eq(ReadRecordEntity::getUserId, TEST_USER_ID)
                .eq(ReadRecordEntity::getTitle, "测试书籍")
                .one();
        assertNotNull(saved, "保存后应能查询到记录");
        assertEquals(TEST_USER_ID, saved.getCreateUser(), "createUser 应为当前登录用户");
        assertEquals(TEST_USER_ID, saved.getUpdateUser(), "updateUser 应为当前登录用户");
        assertNotNull(saved.getCreateTime(), "createTime 不应为空");
        assertNotNull(saved.getUpdateTime(), "updateTime 不应为空");
        assertEquals(0, saved.getIsDeleted(), "isDeleted 应为 0");
    }

    @Test
    void testUpdateRecord_更新审计字段() {
        ReadRecordReq saveReq = new ReadRecordReq();
        saveReq.setTitle("待更新书籍");
        saveReq.setType(1);
        saveReq.setStatus(0);
        readRecordService.saveRecord(saveReq);

        ReadRecordEntity saved = readRecordService.lambdaQuery()
                .eq(ReadRecordEntity::getUserId, TEST_USER_ID)
                .eq(ReadRecordEntity::getTitle, "待更新书籍")
                .one();
        assertNotNull(saved);

        ReadRecordReq updateReq = new ReadRecordReq();
        updateReq.setId(saved.getId());
        updateReq.setTitle("已更新书籍");
        updateReq.setType(1);
        updateReq.setStatus(1);
        readRecordService.updateRecord(updateReq);

        ReadRecordEntity updated = readRecordService.getById(saved.getId());
        assertEquals("已更新书籍", updated.getTitle());
        assertEquals(TEST_USER_ID, updated.getUpdateUser(), "updateUser 应为当前登录用户");
        assertNotNull(updated.getUpdateTime(), "updateTime 不应为空");
        assertFalse(updated.getUpdateTime().isBefore(updated.getCreateTime()),
                "updateTime 不应早于 createTime");
    }
}
