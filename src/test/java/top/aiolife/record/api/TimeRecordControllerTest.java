package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRecordControllerTest {

    @Test
    void testQueryByDateRangeForAI_运动明细仅返回名称和次数() throws Exception {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "TimeRecordControllerTest");
        TableInfoHelper.initTableInfo(builderAssistant, TimeRecordEntity.class);
        TableInfoHelper.initTableInfo(builderAssistant, ExerciseRecordEntity.class);

        TimeRecordEntity timeRecord = new TimeRecordEntity();
        timeRecord.setId("time-1");
        timeRecord.setDate(LocalDate.of(2026, 8, 9));
        timeRecord.setStartTime(540);
        timeRecord.setEndTime(600);

        TimeRecordEntity timeRecordWithoutExercise = new TimeRecordEntity();
        timeRecordWithoutExercise.setId("time-2");
        timeRecordWithoutExercise.setDate(LocalDate.of(2026, 8, 8));
        timeRecordWithoutExercise.setStartTime(540);
        timeRecordWithoutExercise.setEndTime(600);

        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class,
                (method, args) -> "list".equals(method)
                        ? List.of(timeRecord, timeRecordWithoutExercise) : null);

        ExerciseRecordEntity exerciseRecord = new ExerciseRecordEntity();
        exerciseRecord.setTimeId("time-1");
        exerciseRecord.setExerciseTypeId("100");
        exerciseRecord.setExerciseCount(30);
        exerciseRecord.setDescription("不应返回的描述");

        @SuppressWarnings("unchecked")
        BaseMapper<ExerciseRecordEntity> exerciseMapper = proxy(BaseMapper.class,
                (method, args) -> "selectList".equals(method) ? List.of(exerciseRecord) : null);
        IExerciseRecordService exerciseRecordService = proxy(IExerciseRecordService.class,
                (method, args) -> "lambdaQuery".equals(method)
                        ? new LambdaQueryChainWrapper<>(exerciseMapper) : null);

        UserDictDataEntity exerciseType = new UserDictDataEntity();
        exerciseType.setId(100L);
        exerciseType.setDictLabel("俯卧撑");
        UserDictDataService userDictDataService = proxy(UserDictDataService.class,
                (method, args) -> "listUserVisibleDictData".equals(method)
                        ? List.of(exerciseType) : null);
        ITimeTrackerCategoryService categoryService = proxy(ITimeTrackerCategoryService.class,
                (method, args) -> null);

        TimeRecordController controller = new TimeRecordController(
                timeRecordService, exerciseRecordService, categoryService, userDictDataService);
        TimeRecordDateRangeMcpReq req = new TimeRecordDateRangeMcpReq();
        req.setStartDate(LocalDate.of(2026, 8, 9));
        req.setEndDate(LocalDate.of(2026, 8, 9));

        StpLogic originalStpLogic = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override
            public long getLoginIdAsLong() {
                return 1L;
            }
        });
        try {
            List<TimeRecordDateRangeVO> result = controller.queryByDateRangeForAI(req).getData();

            assertEquals(2, result.size());
            assertEquals("俯卧撑", result.getFirst().getExercises().getFirst().getExerciseName());
            assertEquals(30, result.getFirst().getExercises().getFirst().getExerciseCount());

            JsonNode exerciseJson = new ObjectMapper().valueToTree(
                    result.getFirst().getExercises().getFirst());
            assertEquals(2, exerciseJson.size());
            assertTrue(exerciseJson.has("exerciseName"));
            assertTrue(exerciseJson.has("exerciseCount"));
            assertFalse(exerciseJson.has("exerciseTypeId"));
            assertFalse(exerciseJson.has("description"));

            JsonNode recordWithoutExerciseJson = new ObjectMapper()
                    .findAndRegisterModules()
                    .valueToTree(result.getLast());
            assertFalse(recordWithoutExerciseJson.has("exercises"));
        } finally {
            StpUtil.setStpLogic(originalStpLogic);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}
