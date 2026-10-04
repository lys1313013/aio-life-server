package top.aiolife.core;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.JsonConfig;
import top.aiolife.llm.convertor.LlmApiConvertor;
import top.aiolife.llm.pojo.entity.LLMKeyEntity;
import top.aiolife.record.api.CbtiController;
import top.aiolife.record.api.MovieController;
import top.aiolife.record.api.ReadRecordController;
import top.aiolife.record.api.TaskController;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.record.pojo.query.MovieQuery;
import top.aiolife.record.pojo.query.ReadRecordQuery;
import top.aiolife.record.pojo.req.*;
import top.aiolife.record.pojo.vo.MovieVO;
import top.aiolife.record.pojo.vo.ReadRecordVO;
import top.aiolife.record.service.ICbtiService;
import top.aiolife.record.service.IDoubanMovieImportService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证真实 HTTP 边界及所有 Controller 的类型，不连接外部服务。 */
class ApiBoundaryContractTest {
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());

    @BeforeAll
    static void tables() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "api-boundary"), TaskEntity.class);
    }

    @Test
    void 所有业务接口出入参及嵌套对象不使用数据库实体() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int count = 0;
        for (var definition : scanner.findCandidateComponents("top.aiolife")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
                count++;
                inspect(method.getGenericReturnType(), new HashSet<>(), controller.getSimpleName() + "." + method.getName());
                for (Type parameter : method.getGenericParameterTypes()) {
                    inspect(parameter, new HashSet<>(), controller.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertTrue(count > 200, "应扫描全部业务接口");
    }

    private void inspect(Type type, Set<Type> visited, String source) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType generic) {
            inspect(generic.getRawType(), visited, source);
            for (Type argument : generic.getActualTypeArguments()) inspect(argument, visited, source);
            return;
        }
        if (!(type instanceof Class<?> cls) || !cls.getName().startsWith("top.aiolife.") || cls.isEnum()) return;
        assertFalse(cls.getPackageName().contains(".pojo.entity"), source + " 暴露持久化对象 " + cls.getName());
        for (Field field : cls.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) inspect(field.getGenericType(), visited, source + "." + field.getName());
        }
        if (cls.getGenericSuperclass() != null) inspect(cls.getGenericSuperclass(), visited, source);
    }

    @Test
    void HTTP更新忽略所属用户审计逻辑删除及只读字段() throws Exception {
        var mapper = mock(ITaskMapper.class);
        var controller = new TaskController(mock(ITaskService.class), mapper, mock(ITaskDetail.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            mvc.perform(put("/tasks/9007199254740993").contentType(MediaType.APPLICATION_JSON).content("""
                    {"id":"2","userId":"22","createUser":"22","createTime":"2000-01-01 00:00:00",
                     "isDeleted":1,"unCompletedCount":999,"content":"允许修改的内容"}
                    """)).andExpect(status().isOk());
            var saved = ArgumentCaptor.forClass(TaskEntity.class);
            verify(mapper).update(saved.capture(), any(Wrapper.class));
            assertEquals(9007199254740993L, saved.getValue().getId());
            assertEquals("允许修改的内容", saved.getValue().getContent());
            assertNull(saved.getValue().getUserId());
            assertNull(saved.getValue().getCreateUser());
            assertNull(saved.getValue().getCreateTime());
            assertNull(saved.getValue().getIsDeleted());
            assertNull(saved.getValue().getUnCompletedCount());
            assertEquals(11L, saved.getValue().getUpdateUser());
        }
    }

    @Test
    void HTTP新增返回业务字段且大ID为字符串() throws Exception {
        var mapper = mock(ITaskMapper.class);
        when(mapper.insert(any(TaskEntity.class))).thenAnswer(call -> {
            call.<TaskEntity>getArgument(0).setId(9007199254740993L);
            return 1;
        });
        var controller = new TaskController(mock(ITaskService.class), mapper, mock(ITaskDetail.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            JsonNode data = json.readTree(mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"测试任务\",\"userId\":\"22\",\"id\":\"3\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
            assertEquals("9007199254740993", data.path("id").asText());
            assertTrue(data.path("id").isTextual());
            for (String field : List.of("userId", "createUser", "updateUser", "createTime", "updateTime", "isDeleted")) {
                assertFalse(data.has(field), field);
            }
        }
    }

    @Test
    void 关联事件和附件保留空列表及未传的区别() throws Exception {
        var absent = json.readValue("{}", ThoughtUpdateReq.class);
        var empty = json.readValue("{\"events\":[]}", ThoughtUpdateReq.class);
        assertNull(RecordApiConvertor.INSTANCE.fromThoughtUpdateReq(absent).getEvents());
        assertEquals(List.of(), RecordApiConvertor.INSTANCE.fromThoughtUpdateReq(empty).getEvents());
        var nested = json.readValue("{\"events\":[{\"id\":\"9007199254740993\",\"content\":\"事件\",\"thoughtId\":\"22\",\"createUser\":\"22\"}]}", ThoughtUpdateReq.class);
        var event = RecordApiConvertor.INSTANCE.fromThoughtUpdateReq(nested).getEvents().getFirst();
        assertEquals(9007199254740993L, event.getId());
        assertNull(event.getThoughtId());
        assertNull(event.getCreateUser());
        assertNull(RecordApiConvertor.INSTANCE.fromHonorRecordUpdateReq(json.readValue("{}", HonorRecordUpdateReq.class)).getFileIds());
        assertEquals(List.of(), RecordApiConvertor.INSTANCE.fromHonorRecordUpdateReq(json.readValue("{\"fileIds\":[]}", HonorRecordUpdateReq.class)).getFileIds());
    }

    @Test
    void 分类父级未传和显式null不混淆且隐藏状态为业务字段() throws Exception {
        var absent = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryUpdateReq(json.readValue("{}", TimeTrackerCategoryUpdateReq.class));
        var clear = RecordApiConvertor.INSTANCE.fromTimeTrackerCategoryUpdateReq(json.readValue("{\"parentId\":null}", TimeTrackerCategoryUpdateReq.class));
        assertFalse(absent.isParentIdSpecified());
        assertTrue(clear.isParentIdSpecified());
        assertNull(clear.getParentId());
        var hidden = new TimeTrackerCategoryEntity(); hidden.setIsDeleted(1); hidden.setUserId(11L);
        JsonNode result = json.valueToTree(RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVO(hidden));
        assertTrue(result.path("isHidden").asBoolean());
        assertEquals("11", result.path("userId").asText());
        assertFalse(result.has("isDeleted"));
    }

    @Test
    void 模型配置响应只返回密钥存在状态且不含审计信息() {
        var entity = new LLMKeyEntity();
        entity.setApiKey("fixture-secret"); entity.setUserId(11L); entity.setCreateTime(LocalDateTime.now());
        JsonNode result = json.valueToTree(LlmApiConvertor.INSTANCE.toLLMKeyVO(entity));
        assertTrue(result.path("hasApiKey").asBoolean());
        assertFalse(result.has("apiKey")); assertFalse(result.has("userId")); assertFalse(result.has("createTime"));
    }
    @Test
    void 前端正在展示的财务时间和事件时间继续返回() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 1, 12, 30);
        var expense = new ExpenseEntity(); expense.setCreateTime(time); expense.setUpdateTime(time);
        JsonNode expenseJson = json.valueToTree(RecordApiConvertor.INSTANCE.toExpenseVO(expense));
        assertEquals("2026-10-01 12:30:00", expenseJson.path("createTime").asText());
        assertEquals("2026-10-01 12:30:00", expenseJson.path("updateTime").asText());
        var income = new IncomeEntity(); income.setUpdateTime(time);
        assertEquals("2026-10-01 12:30:00", json.valueToTree(RecordApiConvertor.INSTANCE.toIncomeVO(income)).path("updateTime").asText());
        var event = new ThoughtRelaEventEntity(); event.setCreateTime(time);
        assertEquals("2026-10-01 12:30:00", json.valueToTree(RecordApiConvertor.INSTANCE.toThoughtEventVO(event)).path("createTime").asText());
    }

    @Test
    void 观影与阅读分页只返回记录和总数() {
        var movies = mock(IMovieService.class);
        var movie = new MovieVO(); movie.setId("9007199254740993");
        var moviePage = new Page<MovieVO>(2, 20, 41);
        moviePage.setRecords(List.of(movie));
        when(movies.pageList(any())).thenReturn(moviePage);
        var movieController = new MovieController(movies, mock(IDoubanMovieImportService.class));
        JsonNode result = json.valueToTree(movieController.pageList(new MovieQuery()).getData());
        assertEquals(2, result.size());
        assertEquals("9007199254740993", result.path("items").get(0).path("id").asText());
        assertEquals(41, result.path("total").asInt());
        var reads = mock(IReadRecordService.class);
        var readPage = new Page<ReadRecordVO>(1, 20, 0);
        readPage.setRecords(List.of()); when(reads.pageList(any())).thenReturn(readPage);
        var readController = new ReadRecordController(reads);
        result = json.valueToTree(readController.pageList(new ReadRecordQuery()).getData());
        assertEquals(2, result.size()); assertEquals(0, result.path("items").size()); assertEquals(0, result.path("total").asInt());
    }

    @Test
    void 动态人格结果不透传持久化对象和内部归属() {
        var service = mock(ICbtiService.class);
        var personality = new CbtiPersonalityEntity();
        personality.setCode("fixture"); personality.setImageObject("fixture/image.png");
        personality.setCreateTime(LocalDateTime.now()); personality.setIsDeleted(0);
        var history = new HashMap<String, Object>();
        history.put("id", 9007199254740993L); history.put("imageObject", "fixture/image.png");
        var detail = new HashMap<String, Object>();
        detail.put("id", 9007199254740993L); detail.put("userId", 11L); detail.put("personality", personality);
        when(service.getUserHistory(11L)).thenReturn(List.of(history));
        when(service.getHistoryDetail(1L, 11L)).thenReturn(detail);
        var controller = new CbtiController(service, json, new CbtiConfig());
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            JsonNode list = json.valueToTree(controller.results().getData());
            assertFalse(list.get(0).has("imageObject")); assertTrue(list.get(0).has("imageUrl"));
            JsonNode result = json.valueToTree(controller.resultDetail(1L).getData());
            assertFalse(result.has("userId"));
            assertEquals("fixture", result.path("personality").path("code").asText());
            assertFalse(result.path("personality").has("createTime"));
            assertFalse(result.path("personality").has("isDeleted"));
            assertFalse(result.path("personality").has("imageObject"));
        }
    }

}
