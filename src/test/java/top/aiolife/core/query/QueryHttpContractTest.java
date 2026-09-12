package top.aiolife.core.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.api.*;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.sso.api.AuthController;
import top.aiolife.sso.api.UserCenterController;
import top.aiolife.sso.service.IUserService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryHttpContractTest {

    private final ObjectMapper mapper = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());

    private <T> T mock(Class<T> type) {
        // 此处不需要静态或 final mock，避免测试依赖 JVM 动态 attach 权限。
        return org.mockito.Mockito.mock(type, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new QueryParamsArgumentResolver(mapper))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    static Stream<Arguments> readEndpoints() {
        return Stream.of(
                Arguments.of(DeviceController.class, "/device/query"),
                Arguments.of(ReadRecordController.class, "/read-record/page"),
                Arguments.of(UserDictDataController.class, "/userDictData/query"),
                Arguments.of(UserDictDataController.class, "/userDictData/admin/query"),
                Arguments.of(SysDictTypeController.class, "/sysDictType/query"),
                Arguments.of(BVideoController.class, "/b-video/query"),
                Arguments.of(MovieController.class, "/movie/page"),
                Arguments.of(ExpController.class, "/expense/query"),
                Arguments.of(ExpController.class, "/expense/statisticsByYear"),
                Arguments.of(ExpController.class, "/expense/statisticsByMonth"),
                Arguments.of(IncomeController.class, "/income/query"),
                Arguments.of(IncomeController.class, "/income/statisticsByYear"),
                Arguments.of(IncomeController.class, "/income/statisticsByMonth"),
                Arguments.of(TaskColumnController.class, "/taskColumn/query"),
                Arguments.of(TimeRecordController.class, "/timeRecord/query"),
                Arguments.of(TimeRecordController.class, "/timeRecord/queryByDateRange"),
                Arguments.of(TimeRecordController.class, "/timeRecord/queryByDateRangeForAI"),
                Arguments.of(ThoughtController.class, "/thought/query"),
                Arguments.of(SysDictDataController.class, "/sysDictData/query"),
                Arguments.of(ExerciseRecordController.class, "/exerciseRecord/query"),
                Arguments.of(ExerciseRecordController.class, "/exerciseRecord/statistics"),
                Arguments.of(ExerciseRecordController.class, "/exerciseRecord/statistics/light"),
                Arguments.of(MemoController.class, "/memo/query"),
                Arguments.of(UserCenterController.class, "/user-center/list")
        );
    }

    @ParameterizedTest
    @MethodSource("readEndpoints")
    void testReadEndpoint_接受GET并拒绝旧POST(Class<?> type, String path) throws Exception {
        Object controller = mock(type);
        MockMvc mvc = mvc(controller);
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(controller);
        mvc.perform(get(path)).andExpect(status().isOk());
        assertEquals(1, mockingDetails(controller).getInvocations().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExpense_分页和日期条件从URL绑定到泛型DTO() throws Exception {
        ExpController controller = mock(ExpController.class);
        mvc(controller).perform(get("/expense/query")
                        .param("page", "2").param("pageSize", "20")
                        .param("expTypeId", "9007199254740993")
                        .param("startTime", "2026-09-01 08:30:00")
                        .param("endTime", "2026-09-12 23:59:59")
                        .param("remark", "午餐 & 饮料+甜点")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":99,\"condition\":{\"remark\":\"不应读取请求体\"}}"))
                .andExpect(status().isOk());
        ArgumentCaptor<CommonQuery<ExpenseQuery>> captor = ArgumentCaptor.forClass(CommonQuery.class);
        verify(controller).query(captor.capture());
        CommonQuery<ExpenseQuery> query = captor.getValue();
        assertEquals(2, query.getPage());
        assertEquals(20, query.getPageSize());
        assertEquals("9007199254740993", query.getCondition().getExpTypeId());
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 30), query.getCondition().getStartTime());
        assertEquals("午餐 & 饮料+甜点", query.getCondition().getRemark());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeRecord_保留日期大整数和默认分页() throws Exception {
        TimeRecordController controller = mock(TimeRecordController.class);
        mvc(controller).perform(get("/timeRecord/query").param("date", "2026-09-12")
                        .param("categoryId", "9007199254740993"))
                .andExpect(status().isOk());
        ArgumentCaptor<CommonQuery<TimeRecordEntity>> captor = ArgumentCaptor.forClass(CommonQuery.class);
        verify(controller).query(captor.capture());
        assertEquals(1, captor.getValue().getPage());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(LocalDate.of(2026, 9, 12), captor.getValue().getCondition().getDate());
        assertEquals(9007199254740993L, captor.getValue().getCondition().getCategoryId());
    }

    @Test
    void testMovie_支持枚举数组单个枚举和布尔条件() throws Exception {
        for (String[] codes : new String[][]{{"in_progress"}, {"in_progress", "on_hold"}}) {
            MovieController controller = mock(MovieController.class);
            mvc(controller).perform(get("/movie/page").param("statuses", codes)
                            .param("activeOnly", "false").param("current", "3"))
                    .andExpect(status().isOk());
            ArgumentCaptor<MovieQuery> captor = ArgumentCaptor.forClass(MovieQuery.class);
            verify(controller).pageList(captor.capture());
            assertEquals(codes.length, captor.getValue().getStatuses().size());
            assertEquals(ProgressStatusEnum.IN_PROGRESS, captor.getValue().getStatuses().getFirst());
            assertFalse(captor.getValue().getActiveOnly());
            assertEquals(3, captor.getValue().getCurrent());
        }
    }

    @Test
    void testInvalidQuery_非法日期和枚举返回400且不调用业务() throws Exception {
        ExpController expense = mock(ExpController.class);
        mvc(expense).perform(get("/expense/query").param("startTime", "not-a-date"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(expense);
        MovieController movie = mock(MovieController.class);
        mvc(movie).perform(get("/movie/page").param("statuses", "unknown"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(movie);
    }

    @Test
    void testVerificationCode_仅POST请求体发送且保留客户端IP() throws Exception {
        IUserService service = mock(IUserService.class);
        MockMvc mvc = mvc(new AuthController(service));
        for (String path : List.of("/auth/sendEmailCode", "/auth/sendResetPasswordCode")) {
            mvc.perform(get(path).param("email", "user@example.com"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "POST"));
        }
        verifyNoInteractions(service);
        mvc.perform(post("/auth/sendEmailCode").contentType(MediaType.APPLICATION_JSON)
                        .header("x-forwarded-for", "192.0.2.1")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"));
        verify(service).sendRegisterCode("user@example.com", "192.0.2.1");
        mvc.perform(post("/auth/sendResetPasswordCode").contentType(MediaType.APPLICATION_JSON)
                        .header("x-forwarded-for", "192.0.2.2")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk());
        verify(service).sendResetPasswordCode("user@example.com", "192.0.2.2");
        verifyNoMoreInteractions(service);
    }

    @Test
    void testVerificationCode_缺少邮箱不发送() throws Exception {
        IUserService service = mock(IUserService.class);
        mvc(new AuthController(service)).perform(post("/auth/sendEmailCode")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void testNotification_GET不触发发送() throws Exception {
        LeetcodeController controller = mock(LeetcodeController.class);
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/leetcode/notifyTodayQuestion")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(controller);
        mvc.perform(post("/leetcode/notifyTodayQuestion")).andExpect(status().isOk());
        verify(controller).notifyTodayQuestion();
    }
}
