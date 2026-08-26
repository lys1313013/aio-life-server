package top.aiolife.record.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.service.IMovieService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MovieServiceImpl 集成测试
 *
 * @author Lys
 * @date 2026/08/26
 */
class MovieServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IMovieService movieService;

    @Test
    void testSaveRecord_审计字段自动填充() {
        MovieReq req = new MovieReq();
        req.setTitle("测试电影");
        req.setType(1);
        req.setStatus(0);

        movieService.saveRecord(req);

        MovieEntity saved = movieService.lambdaQuery()
                .eq(MovieEntity::getUserId, TEST_USER_ID)
                .eq(MovieEntity::getTitle, "测试电影")
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
        MovieReq saveReq = new MovieReq();
        saveReq.setTitle("待更新电影");
        saveReq.setType(1);
        saveReq.setStatus(0);
        movieService.saveRecord(saveReq);

        MovieEntity saved = movieService.lambdaQuery()
                .eq(MovieEntity::getUserId, TEST_USER_ID)
                .eq(MovieEntity::getTitle, "待更新电影")
                .one();
        assertNotNull(saved);

        MovieReq updateReq = new MovieReq();
        updateReq.setId(saved.getId());
        updateReq.setTitle("已更新电影");
        updateReq.setType(1);
        updateReq.setStatus(1);
        movieService.updateRecord(updateReq);

        MovieEntity updated = movieService.getById(saved.getId());
        assertEquals("已更新电影", updated.getTitle());
        assertEquals(TEST_USER_ID, updated.getUpdateUser(), "updateUser 应为当前登录用户");
        assertNotNull(updated.getUpdateTime(), "updateTime 不应为空");
        assertFalse(updated.getUpdateTime().isBefore(updated.getCreateTime()),
                "updateTime 不应早于 createTime");
    }
}
