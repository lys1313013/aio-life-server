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
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRecordControllerTest {

    @Test
    void testRecommendNext_全天已满时返回空推荐及当天记录且不查询分类() {
        TimeRecordEntity record = new TimeRecordEntity();
        record.setStartTime(0);
        record.setEndTime(1439);
        List<TimeRecordEntity> records = List.of(record);
        RecommendNextVO result = new RecommendNextVO(null, records);
        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class, (method, args) -> {
            assertEquals("recommendNext", method, "没有剩余时间时不应查询推荐分类");
            return result;
        });

        ApiResponse<RecommendNextVO> response = recommendNextAsUser(timeRecordService);

        assertEquals("0", response.getRscode());
        assertNull(response.getData().getRecommend());
        assertSame(records, response.getData().getRecords());
    }

    @Test
    void testRecommendNext_剩余一分钟时正常推荐并查询分类() {
        TimeRecordEntity previousRecord = new TimeRecordEntity();
        previousRecord.setEndTime(1438);
        previousRecord.setCategoryId(10L);
        TimeRecordEntity recommend = new TimeRecordEntity();
        recommend.setStartTime(1439);
        recommend.setEndTime(1439);
        RecommendNextVO result = new RecommendNextVO(recommend, List.of(previousRecord));
        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class, (method, args) -> {
            if ("recommendNext".equals(method)) {
                return result;
            }
            assertEquals("recommendType", method);
            assertEquals(1L, args[0]);
            assertEquals("2026-09-12", args[1]);
            assertEquals(1439, args[2]);
            assertEquals(10L, args[3]);
            return 20L;
        });

        ApiResponse<RecommendNextVO> response = recommendNextAsUser(timeRecordService);

        assertEquals("0", response.getRscode());
        assertSame(recommend, response.getData().getRecommend());
        assertEquals(20L, recommend.getCategoryId());
    }

    private ApiResponse<RecommendNextVO> recommendNextAsUser(ITimeRecordService timeRecordService) {
        TimeRecordController controller = new TimeRecordController(timeRecordService, null, null, null);
        StpLogic originalStpLogic = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override
            public long getLoginIdAsLong() {
                return 1L;
            }
        });
        try {
            return controller.recommendNext("2026-09-12");
        } finally {
            StpUtil.setStpLogic(originalStpLogic);
        }
    }

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
        exerciseRecord.setExerciseTypeId(100L);
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
