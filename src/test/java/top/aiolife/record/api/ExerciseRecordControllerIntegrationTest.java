package top.aiolife.record.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.query.ExerciseRecordQuery;
import top.aiolife.record.pojo.query.ExerciseStatisticsQuery;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExerciseRecordController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class ExerciseRecordControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExerciseRecordController exerciseRecordController;

    @Autowired
    private IExerciseRecordMapper exerciseRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testQuery_查询运动记录() {
        Long exerciseId = System.currentTimeMillis() % 1000000 + 900000L;
        exerciseRecordMapper.insert(createExerciseRecord(exerciseId));

        CommonQuery<ExerciseRecordEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new ExerciseRecordEntity());

        var response = exerciseRecordController.query(ApiRequestFixtures.query(query, ExerciseRecordQuery.class));
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    @Test
    void testGetById_根据ID查询运动记录() {
        Long exerciseId = System.currentTimeMillis() % 1000000 + 900000L;
        exerciseRecordMapper.insert(createExerciseRecord(exerciseId));

        var response = exerciseRecordController.get(exerciseId);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertEquals(exerciseId, response.getData().getId());
    }

    @Test
    void testGetStatistics_获取统计数据() {
        Long exerciseId = System.currentTimeMillis() % 1000000 + 900000L;
        exerciseRecordMapper.insert(createExerciseRecord(exerciseId));

        Map<String, Object> params = new HashMap<>();
        params.put("startDate", LocalDate.now().minusMonths(1).toString());
        params.put("endDate", LocalDate.now().plusDays(1).toString());

        var response = exerciseRecordController.getStatistics(ApiRequestFixtures.request(params, ExerciseStatisticsQuery.class));
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testStatisticsSummary_超过千条完整聚合且隔离用户与软删除() throws Exception {
        long typeId = 9223372036854775000L;
        LocalDate date = LocalDate.of(2023, 1, 2);
        for (int i = 0; i < 1005; i++) {
            ExerciseRecordEntity entity = createExerciseRecord(null);
            entity.setExerciseTypeId(typeId);
            entity.setExerciseDate(date);
            entity.setExerciseCount(i == 0 ? null : 2);
            exerciseRecordMapper.insert(entity);
        }
        ExerciseRecordEntity other = createExerciseRecord(null);
        other.setExerciseTypeId(typeId);
        other.setExerciseDate(date);
        other.setUserId(TEST_USER_ID + 1);
        exerciseRecordMapper.insert(other);
        ExerciseRecordEntity deleted = createExerciseRecord(null);
        deleted.setExerciseTypeId(typeId);
        deleted.setExerciseDate(date);
        exerciseRecordMapper.insert(deleted);
        exerciseRecordMapper.deleteById(deleted.getId());

        var filters = new ExerciseStatisticsQuery();
        filters.setExerciseTypeId(typeId);
        var points = assertSuccessWithData(exerciseRecordController.getStatisticsSummary(filters));
        assertEquals(1, points.size());
        assertEquals(1005, points.getFirst().getRecordCount());
        assertEquals(0, new BigDecimal("2008").compareTo(points.getFirst().getExerciseCount()));
        var json = objectMapper.readTree(objectMapper.writeValueAsString(points.getFirst()));
        assertEquals(String.valueOf(typeId), json.get("exerciseTypeId").textValue());
        assertTrue(json.get("recordCount").isIntegralNumber());
        assertTrue(json.get("exerciseCount").isNumber());
        // 不传日期也包含一年以前的记录。
        assertTrue(assertSuccessWithData(exerciseRecordController.getStatisticsSummary(new ExerciseStatisticsQuery()))
                .stream().anyMatch(point -> point.getExerciseTypeId().equals(typeId)));
    }

    @Test
    void testStatisticsSummary_日期类型备注筛选与分页列表一致() {
        long typeId = 9223372036854774999L;
        LocalDate date = LocalDate.of(2023, 2, 1);
        for (int i = 0; i < 3; i++) {
            var entity = createExerciseRecord(null);
            entity.setExerciseTypeId(typeId);
            entity.setExerciseDate(date.plusDays(i));
            entity.setDescription(i == 2 ? "其他" : "边界筛选");
            exerciseRecordMapper.insert(entity);
        }
        var filters = new ExerciseStatisticsQuery();
        filters.setExerciseTypeId(typeId);
        filters.setStartDate(date.toString());
        filters.setEndDate(date.plusDays(1).toString());
        filters.setDescription("边界");
        var points = assertSuccessWithData(exerciseRecordController.getStatisticsSummary(filters));
        assertEquals(2, points.size());
        assertEquals(date.plusDays(1), points.getFirst().getExerciseDate());

        var query = new CommonQuery<ExerciseRecordQuery>();
        var condition = new ExerciseRecordQuery();
        condition.setExerciseTypeId(typeId);
        condition.setStartDate(date);
        condition.setEndDate(date.plusDays(1));
        condition.setDescription("边界");
        query.setCondition(condition);
        query.setPageSize(1);
        var page = assertSuccessWithData(exerciseRecordController.query(query));
        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(points.getFirst().getExerciseDate(), page.getItems().getFirst().getExerciseDate());
        filters.setStartDate(date.plusDays(3).toString());
        assertTrue(assertSuccessWithData(exerciseRecordController.getStatisticsSummary(filters)).isEmpty());
    }

    private ExerciseRecordEntity createExerciseRecord(Long exerciseId) {
        ExerciseRecordEntity entity = new ExerciseRecordEntity();
        entity.setId(exerciseId);
        entity.setUserId(TEST_USER_ID);
        entity.setCreateUser(TEST_USER_ID);
        entity.setExerciseTypeId(1L);
        entity.setExerciseDate(LocalDate.now());
        entity.setExerciseCount(1);
        entity.setDescription("测试运动记录");
        return entity;
    }
}
