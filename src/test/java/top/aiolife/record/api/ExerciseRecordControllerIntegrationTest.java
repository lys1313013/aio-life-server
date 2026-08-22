package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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

    @Test
    void testQuery_查询运动记录() {
        Long exerciseId = System.currentTimeMillis() % 1000000 + 900000L;
        exerciseRecordMapper.insert(createExerciseRecord(exerciseId));

        CommonQuery<ExerciseRecordEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new ExerciseRecordEntity());

        var response = exerciseRecordController.query(query);
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

        var response = exerciseRecordController.getStatistics(params);
        assertSuccess(response);
        assertNotNull(response.getData());
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
