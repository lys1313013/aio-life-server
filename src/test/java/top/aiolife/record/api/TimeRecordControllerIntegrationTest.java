package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.enums.RelateTypeEnum;
import top.aiolife.record.pojo.query.TimeWeekQuery;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.ITimeRecordService;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeRecordController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class TimeRecordControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TimeRecordController timeRecordController;

    @Autowired
    private ITimeRecordMapper timeRecordMapper;

    @Autowired
    private ITimeRecordService timeRecordService;

    @Autowired
    private IReadRecordService readRecordService;

    @Autowired
    private IMovieService movieService;

    @Test
    void testSaveTimeRecord_只更新本人关联的阅读状态() {
        ReadRecordEntity other = new ReadRecordEntity();
        other.setTitle("其他用户的阅读");
        other.setType(1);
        other.setStatus(ProgressStatusEnum.NOT_STARTED);
        other.setUserId(2L);
        other.fillCreateCommonField(2L);
        readRecordService.save(other);

        ReadRecordEntity own = new ReadRecordEntity();
        own.setTitle("自己的阅读");
        own.setType(1);
        own.setStatus(ProgressStatusEnum.NOT_STARTED);
        own.setUserId(TEST_USER_ID);
        own.fillCreateCommonField(TEST_USER_ID);
        readRecordService.save(own);

        TimeRecordReq foreignReq = timeRecordReq();
        foreignReq.setRelateType(RelateTypeEnum.READ.getValue());
        foreignReq.setRelateId(other.getId());
        timeRecordService.saveTimeRecord(foreignReq);
        assertEquals(ProgressStatusEnum.NOT_STARTED, readRecordService.getById(other.getId()).getStatus());

        TimeRecordReq ownReq = timeRecordReq();
        ownReq.setRelateType(RelateTypeEnum.READ.getValue());
        ownReq.setRelateId(own.getId());
        timeRecordService.saveTimeRecord(ownReq);
        assertEquals(ProgressStatusEnum.IN_PROGRESS, readRecordService.getById(own.getId()).getStatus());
    }

    @Test
    void testUpdateTimeRecord_只更新本人关联的影视状态() {
        MovieEntity other = new MovieEntity();
        other.setTitle("其他用户的影视");
        other.setType(1);
        other.setStatus(ProgressStatusEnum.NOT_STARTED);
        other.setUserId(2L);
        other.fillCreateCommonField(2L);
        movieService.save(other);

        MovieEntity own = new MovieEntity();
        own.setTitle("自己的影视");
        own.setType(1);
        own.setStatus(ProgressStatusEnum.NOT_STARTED);
        own.setUserId(TEST_USER_ID);
        own.fillCreateCommonField(TEST_USER_ID);
        movieService.save(own);

        TimeRecordReq req = timeRecordReq();
        req.setId(timeRecordService.saveTimeRecord(req));
        req.setRelateType(RelateTypeEnum.MOVIE.getValue());
        req.setRelateId(other.getId());
        timeRecordService.updateTimeRecord(req);
        assertEquals(ProgressStatusEnum.NOT_STARTED, movieService.getById(other.getId()).getStatus());

        req.setRelateId(own.getId());
        timeRecordService.updateTimeRecord(req);
        assertEquals(ProgressStatusEnum.IN_PROGRESS, movieService.getById(own.getId()).getStatus());
    }

    private TimeRecordReq timeRecordReq() {
        TimeRecordReq req = new TimeRecordReq();
        req.setDate(LocalDate.now());
        req.setStartTime(540);
        req.setEndTime(569);
        req.setTitle("关联状态归属测试");
        return req;
    }

    @Test
    void testQuery_查询时间记录() {
        String id = "test_time_" + System.currentTimeMillis();
        timeRecordMapper.insert(createTimeRecord(id, LocalDate.now()));

        CommonQuery<TimeRecordEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        TimeRecordEntity condition = new TimeRecordEntity();
        condition.setDate(LocalDate.now());
        query.setCondition(condition);

        var response = timeRecordController.query(query);
        assertSuccess(response);
        PageResp<TimeRecordEntity> pageResp = response.getData();
        assertNotNull(pageResp);
        assertTrue(pageResp.getTotal() >= 1);

        boolean found = pageResp.getItems().stream()
                .anyMatch(r -> id.equals(r.getId()));
        assertTrue(found, "应该包含刚才插入的记录");
    }

    @Test
    void testQueryByDateRange_按日期范围查询() {
        String id = "test_timerange_" + System.currentTimeMillis();
        timeRecordMapper.insert(createTimeRecord(id, LocalDate.now()));

        CommonQuery<TimeWeekQuery> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(100);
        TimeWeekQuery condition = new TimeWeekQuery();
        condition.setStartDate(LocalDate.now().minusDays(1));
        condition.setEndDate(LocalDate.now().plusDays(1));
        query.setCondition(condition);

        var response = timeRecordController.queryByDateRange(query);
        assertSuccess(response);
        List<TimeRecordEntity> list = response.getData();
        assertNotNull(list);
    }

    @Test
    void testGetById_根据ID查询() {
        String id = "test_getbyid_" + System.currentTimeMillis();
        timeRecordMapper.insert(createTimeRecord(id, LocalDate.now()));

        var response = timeRecordController.getById(id);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertEquals(id, response.getData().getId());
    }

    private TimeRecordEntity createTimeRecord(String id, LocalDate date) {
        TimeRecordEntity entity = new TimeRecordEntity();
        entity.setId(id);
        entity.setUserId(TEST_USER_ID);
        entity.setCreateUser(TEST_USER_ID);
        entity.setDate(date);
        entity.setStartTime(540);
        entity.setEndTime(600);
        entity.setTitle("测试时间记录");
        entity.setCategoryId(1L);
        entity.setDuration(60);
        return entity;
    }
}
