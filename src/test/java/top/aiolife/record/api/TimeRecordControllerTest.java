package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.TimeRecordRecommendationVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.sso.service.SecondaryLockGuard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeRecordControllerTest {

    @Test
    void testRecommendNext_全天已满时返回空推荐及当天记录且不查询分类() {
        TimeRecordEntity record = new TimeRecordEntity();
        record.setStartMinute(0);
        record.setEndMinute(1439);
        List<TimeRecordEntity> records = List.of(record);
        RecommendNextVO result = new RecommendNextVO(null, records);
        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class, (method, args) -> {
            assertEquals("recommendNextWithCategory", method, "没有剩余时间时不应查询推荐分类");
            return result;
        });

        var response = recommendNextAsUser(timeRecordService);

        assertEquals(0, response.getCode());
        assertNull(response.getData().getRecommend());
        assertEquals(RecordApiConvertor.INSTANCE.toTimeRecordListVOList(records), response.getData().getRecords());
    }

    @Test
    void testRecommendNext_剩余一分钟时正常推荐并查询分类() {
        TimeRecordEntity previousRecord = new TimeRecordEntity();
        previousRecord.setEndMinute(1438);
        previousRecord.setCategoryId(10L);
        TimeRecordEntity recommend = new TimeRecordEntity();
        recommend.setStartMinute(1439);
        recommend.setEndMinute(1439);
        RecommendNextVO result = new RecommendNextVO(recommend, List.of(previousRecord));
        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class, (method, args) -> {
            assertEquals("recommendNextWithCategory", method);
            assertEquals(1L, args[0]);
            assertEquals("2026-09-12", args[1]);
            assertEquals(2, args.length);
            recommend.setCategoryId(20L);
            return result;
        });

        var response = recommendNextAsUser(timeRecordService);

        assertEquals(0, response.getCode());
        assertEquals(RecordApiConvertor.INSTANCE.toTimeRecordListVO(recommend), response.getData().getRecommend());
        assertEquals(20L, recommend.getCategoryId());
    }

    private ApiResponse<TimeRecordRecommendationVO> recommendNextAsUser(ITimeRecordService timeRecordService) {
        TimeRecordController controller = new TimeRecordController(timeRecordService);
        StpLogic originalStpLogic = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override
            public long getLoginIdAsLong() {
                return 1L;
            }
        });
        try {
            return controller.recommendNext("1", "2026-09-12");
        } finally {
            StpUtil.setStpLogic(originalStpLogic);
        }
    }

    @Test
    void testQueryByDateRangeForAI_运动明细仅返回名称和次数() throws Exception {
        TimeRecordDateRangeVO withExercise = new TimeRecordDateRangeVO();
        withExercise.setId("time-1");
        withExercise.setDate(LocalDate.of(2026, 8, 9));
        withExercise.setStartMinute(540);
        withExercise.setEndMinute(600);
        withExercise.setExercises(List.of(new top.aiolife.record.pojo.vo.TimeRecordExerciseVO("俯卧撑", 30)));

        TimeRecordDateRangeVO withoutExercise = new TimeRecordDateRangeVO();
        withoutExercise.setId("time-2");
        withoutExercise.setDate(LocalDate.of(2026, 8, 8));
        withoutExercise.setStartMinute(540);
        withoutExercise.setEndMinute(600);

        ITimeRecordService timeRecordService = proxy(ITimeRecordService.class, (method, args) -> {
            assertEquals("queryByDateRangeForAI", method);
            assertEquals(1L, args[0]);
            return List.of(withExercise, withoutExercise);
        });

        TimeRecordController controller = new TimeRecordController(timeRecordService);
        TimeRecordDateRangeMcpReq req = new TimeRecordDateRangeMcpReq();
        req.setStartDate(LocalDate.of(2026, 8, 9));
        req.setEndDate(LocalDate.of(2026, 8, 9));

        List<TimeRecordDateRangeVO> result = controller.queryByDateRangeForAI("1", req).getData();

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
