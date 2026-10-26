package top.aiolife.core.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.bankcard.api.BankCardCoverTemplateAdminController;
import top.aiolife.bankcard.pojo.query.BankCardCoverQuery;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.api.ExerciseRecordController;
import top.aiolife.record.api.MovieController;
import top.aiolife.record.api.GitHubController;
import top.aiolife.record.api.WereadController;
import top.aiolife.record.pojo.query.WereadRecentQuery;
import top.aiolife.record.api.PerformanceController;
import top.aiolife.record.api.ReadRecordController;
import top.aiolife.record.api.TaskController;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.query.TaskPageQuery;
import top.aiolife.record.service.PerformanceRecordService;
import top.aiolife.sso.api.MessageController;
import top.aiolife.sso.pojo.query.MessagePageQuery;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaginationHttpContractTest {
    private final ObjectMapper mapper = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new QueryParamsArgumentResolver(mapper))
                .setControllerAdvice(new ExceptionHandle()).build();
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(ExerciseRecordController.class, "/exerciseRecord/query", 200),
                Arguments.of(MovieController.class, "/movie/page", 200),
                Arguments.of(GitHubController.class, "/github/recent-commits", 100),
                Arguments.of(ReadRecordController.class, "/read-record/page", 200),
                Arguments.of(TaskController.class, "/tasks", 200),
                Arguments.of(MessageController.class, "/message/page", 50),
                Arguments.of(MessageController.class, "/message/admin/list", 200),
                Arguments.of(BankCardCoverTemplateAdminController.class, "/system/bank-card-covers/page", 100));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void testPagination_非法参数在调用业务前返回400(Class<?> type, String path, int limit) throws Exception {
        Object controller = mock(type);
        MockMvc mvc = mvc(controller);
        for (String invalid : List.of("0", "-1", "abc", "1.5", "2147483648", "")) {
            mvc.perform(get(path).param("page", invalid)).andExpect(status().isBadRequest());
            mvc.perform(get(path).param("pageSize", invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).param("pageSize", String.valueOf(limit + 1))).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("page", "1", "2")).andExpect(status().isBadRequest());
        verifyNoInteractions(controller);
        mvc.perform(get(path).param("page", "2").param("pageSize", String.valueOf(limit))).andExpect(status().isOk());
        assertEquals(1, mockingDetails(controller).getInvocations().size());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void testPagination_只接受当前参数名(Class<?> type, String path, int limit) throws Exception {
        Object controller = mock(type);
        for (String alias : List.of("current", "size", "get", "perPage")) {
            mvc(controller).perform(get(path).param(alias, "2"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(controller);
    }

    @Test
    void testTask_默认每页100条() throws Exception {
        TaskController controller = mock(TaskController.class);
        mvc(controller).perform(get("/tasks").param("page", "3")).andExpect(status().isOk());
        ArgumentCaptor<TaskPageQuery> query = ArgumentCaptor.forClass(TaskPageQuery.class);
        verify(controller).queryPage(query.capture());
        assertEquals(3, query.getValue().getPage());
        assertEquals(100, query.getValue().getPageSize());
    }

    @Test
    void testWeread_统一pageSize并保持游标分页和上限20() throws Exception {
        WereadController controller = mock(WereadController.class);
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/weread/recent").param("cursor", "123:book").param("pageSize", "20"))
                .andExpect(status().isOk());
        ArgumentCaptor<WereadRecentQuery> query = ArgumentCaptor.forClass(WereadRecentQuery.class);
        verify(controller).recent(query.capture());
        assertEquals("123:book", query.getValue().getCursor());
        assertEquals(20, query.getValue().getPageSize());
        clearInvocations(controller);
        mvc.perform(get("/weread/recent").param("pageSize", "21")).andExpect(status().isBadRequest());
        mvc.perform(get("/weread/recent").param("size", "7")).andExpect(status().isBadRequest());
        verifyNoInteractions(controller);
    }

    @Test
    void testPerformance_非法分页不调用业务且直接CommonQuery也拒绝越界() throws Exception {
        PerformanceRecordService service = mock(PerformanceRecordService.class);
        MockMvc mvc = mvc(new PerformanceController(service));
        mvc.perform(get("/performance").param("pageSize", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/performance").param("pageSize", "201")).andExpect(status().isBadRequest());
        mvc.perform(get("/performance").param("page", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        CommonQuery<Object> query = new CommonQuery<>();
        assertThrows(IllegalArgumentException.class, () -> query.setPage(0));
        assertThrows(IllegalArgumentException.class, () -> query.setPageSize(-1));
        assertThrows(IllegalArgumentException.class, () -> query.setPageSize(201));
        assertThrows(IllegalArgumentException.class, () -> query.setPageSize(null));
    }
}
