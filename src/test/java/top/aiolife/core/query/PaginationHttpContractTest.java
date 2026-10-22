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

    @Test
    void testMovie_新旧分页参数等值兼容且冲突被拒绝() throws Exception {
        MovieController controller = mock(MovieController.class);
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/movie/page").param("page", "2").param("current", "2")
                .param("pageSize", "30").param("size", "30")).andExpect(status().isOk());
        ArgumentCaptor<MovieQuery> query = ArgumentCaptor.forClass(MovieQuery.class);
        verify(controller).pageList(query.capture());
        assertEquals(2, query.getValue().getPage());
        assertEquals(30, query.getValue().getPageSize());
        clearInvocations(controller);
        mvc.perform(get("/movie/page").param("current", "3").param("size", "20")).andExpect(status().isOk());
        verify(controller).pageList(query.capture());
        assertEquals(3, query.getValue().getPage());
        assertEquals(20, query.getValue().getPageSize());
        clearInvocations(controller);
        mvc.perform(get("/movie/page").param("page", "2").param("current", "3")).andExpect(status().isBadRequest());
        mvc.perform(get("/movie/page").param("pageSize", "20").param("size", "30")).andExpect(status().isBadRequest());
        mvc.perform(get("/movie/page").param("size", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(controller);
    }

    @Test
    void testTask_兼容get并保留默认每页100条() throws Exception {
        TaskController controller = mock(TaskController.class);
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/tasks").param("get", "3")).andExpect(status().isOk());
        ArgumentCaptor<TaskPageQuery> query = ArgumentCaptor.forClass(TaskPageQuery.class);
        verify(controller).queryPage(query.capture());
        assertEquals(3, query.getValue().getPage());
        assertEquals(100, query.getValue().getPageSize());
        clearInvocations(controller);
        mvc.perform(get("/tasks").param("get", "2").param("page", "3")).andExpect(status().isBadRequest());
        verifyNoInteractions(controller);
    }

    @Test
    void testMessageAndCover_保留原别名和更严格上限() throws Exception {
        MessageController messages = mock(MessageController.class);
        mvc(messages).perform(get("/message/page").param("current", "2").param("size", "50"))
                .andExpect(status().isOk());
        ArgumentCaptor<MessagePageQuery> message = ArgumentCaptor.forClass(MessagePageQuery.class);
        verify(messages).queryPage(message.capture());
        assertEquals(2, message.getValue().getPage());
        assertEquals(50, message.getValue().getPageSize());
        clearInvocations(messages);
        mvc(messages).perform(get("/message/page").param("size", "51")).andExpect(status().isBadRequest());
        verifyNoInteractions(messages);
        BankCardCoverTemplateAdminController covers = mock(BankCardCoverTemplateAdminController.class);
        mvc(covers).perform(get("/system/bank-card-covers/page").param("size", "24"))
                .andExpect(status().isOk());
        ArgumentCaptor<BankCardCoverQuery> cover = ArgumentCaptor.forClass(BankCardCoverQuery.class);
        verify(covers).page(cover.capture());
        assertEquals(24, cover.getValue().getPageSize());
    }

    @Test
    void testWeread_统一pageSize并保持游标分页和上限20() throws Exception {
        WereadController controller = mock(WereadController.class);
        MockMvc mvc = mvc(controller);
        mvc.perform(get("/weread/recent").param("cursor", "123:book").param("size", "20"))
                .andExpect(status().isOk());
        ArgumentCaptor<WereadRecentQuery> query = ArgumentCaptor.forClass(WereadRecentQuery.class);
        verify(controller).recent(query.capture());
        assertEquals("123:book", query.getValue().getCursor());
        assertEquals(20, query.getValue().getPageSize());
        clearInvocations(controller);
        mvc.perform(get("/weread/recent").param("pageSize", "21")).andExpect(status().isBadRequest());
        mvc.perform(get("/weread/recent").param("pageSize", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/weread/recent").param("pageSize", "6").param("size", "7")).andExpect(status().isBadRequest());
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
