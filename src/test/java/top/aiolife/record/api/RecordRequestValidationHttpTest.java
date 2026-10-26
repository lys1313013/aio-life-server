package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.mapper.ITaskColumnMapper;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.req.ExerciseRecordCreateReq;
import top.aiolife.record.pojo.req.TaskCreateReq;
import top.aiolife.record.pojo.req.TaskDetailCreateReq;
import top.aiolife.record.pojo.req.ExerciseRecordUpdateReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.req.TaskUpdateReq;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.ITaskColumnService;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.sso.service.SecondaryLockGuard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通过真实控制器、JSON 配置和 Bean Validation 验证请求边界；持久化层用 mock 隔离。 */
class RecordRequestValidationHttpTest {
    private final ITaskMapper tasks = mock(ITaskMapper.class);
    private final ITaskDetail details = mock(ITaskDetail.class);
    private final IExerciseRecordMapper exercises = mock(IExerciseRecordMapper.class);
    private final ITaskColumnMapper columns = mock(ITaskColumnMapper.class);
    private final ITimeRecordService timeRecords = mock(ITimeRecordService.class);
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ITaskService taskService = mock(ITaskService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new TaskController(taskService, tasks, details),
                        new TaskDetailController(details, taskService),
                        new TaskColumnController(columns, mock(ITaskColumnService.class)),
                        new ExerciseRecordController(exercises, mock(IExerciseRecordService.class)),
                        new TimeRecordController(mock(SecondaryLockGuard.class), timeRecords,
                                mock(IExerciseRecordService.class), mock(ITimeTrackerCategoryService.class),
                                mock(UserDictDataService.class)))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void testCreate_缺少必填字段不能进入持久化层() throws Exception {
        for (String path : List.of("/tasks", "/taskDetails", "/taskColumn", "/exerciseRecord")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        }
        verifyNoInteractions(tasks, details, columns, exercises);
    }

    @Test
    void testExercise_负数量在新增更新和时迹内嵌请求都被拒绝() throws Exception {
        mvc.perform(post("/exerciseRecord").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseTypeId\":\"9007199254740993\",\"exerciseDate\":\"2026-10-22\",\"exerciseCount\":-5}"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        mvc.perform(put("/exerciseRecord/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseCount\":-5}"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        for (var request : List.of(post("/timeRecord"), put("/timeRecord/1"))) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                            .content("{\"exercises\":[{\"exerciseTypeId\":\"1\",\"exerciseCount\":-5}]}"))
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        }
        verifyNoInteractions(exercises, timeRecords);
    }

    @Test
    void testTaskDates_严格使用空格格式() throws Exception {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(put("/tasks/9007199254740993").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"dueDate\":\"2026-10-22 10:30:00\"}"))
                    .andExpect(jsonPath("$.code").value(0));
            var task = ArgumentCaptor.forClass(TaskEntity.class);
            verify(tasks).update(task.capture(), any(Wrapper.class));
            assertEquals(9007199254740993L, task.getValue().getId());
            assertEquals(LocalDateTime.of(2026, 10, 22, 10, 30), task.getValue().getDueDate());

            mvc.perform(put("/taskDetails").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"9007199254740993\",\"startTime\":\"2026-10-22 10:30:00\",\"endTime\":\"2026-10-22 10:30:00\"}"))
                    .andExpect(jsonPath("$.code").value(0));
            var detail = ArgumentCaptor.forClass(TaskDetailEntity.class);
            verify(details).update(detail.capture(), any(Wrapper.class));
            assertEquals(detail.getValue().getStartTime(), detail.getValue().getEndTime());
        }
    }

    @Test
    void testMalformedInput_无效日期和类型以及缺少参数返回统一参数错误() throws Exception {
        for (String payload : List.of("{", "{\"dueDate\":\"2026-02-30 10:30:00\"}",
                "{\"dueDate\":\"2026-10-22T10:30:00\"}", "{\"dueDate\":\"2026-10-22T10:30:00Z\"}", "{\"dueDate\":123}")) {
            mvc.perform(put("/tasks/1").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        }
        mvc.perform(get("/taskDetails"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        mvc.perform(get("/taskDetails").param("taskId", "undefined"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        verifyNoInteractions(tasks, details);
    }

    @Test
    void testUpdate_保留可选字段与日期空值且拒绝空白标题和非法状态() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new TaskUpdateReq()).isEmpty());
            assertTrue(validator.validate(new ExerciseRecordUpdateReq()).isEmpty());
            var update = json.readValue("{\"id\":\"1\",\"startTime\":null,\"endTime\":null}", TaskDetailUpdateReq.class);
            assertTrue(validator.validate(update).isEmpty());
            assertNull(update.getStartTime());
            assertNull(update.getEndTime());
        }
        mvc.perform(put("/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \\n\"}"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        mvc.perform(put("/taskDetails").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"1\",\"isCompleted\":2}"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        verifyNoInteractions(tasks, details);
    }

    @Test
    void testCreate_合法必填字段和零数量可通过且创建日期采用同一格式() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var exercise = json.readValue("{\"exerciseTypeId\":\"9007199254740993\",\"exerciseDate\":\"2026-10-22\",\"exerciseCount\":0}", ExerciseRecordCreateReq.class);
            assertTrue(validator.validate(exercise).isEmpty());
            exercise.setExerciseCount(null);
            assertTrue(validator.validate(exercise).isEmpty());

            var task = json.readValue("{\"columnId\":\"1\",\"content\":\"任务\",\"dueDate\":\"2026-10-22 10:30:00\"}", TaskCreateReq.class);
            assertTrue(validator.validate(task).isEmpty());
            var detail = json.readValue("{\"taskId\":\"1\",\"content\":\"明细\",\"startTime\":\"2026-10-22 10:30:00\",\"endTime\":\"2026-10-22 10:30:00\"}", TaskDetailCreateReq.class);
            assertTrue(validator.validate(detail).isEmpty());
            assertEquals(task.getDueDate(), detail.getStartTime());
            assertEquals(detail.getStartTime(), detail.getEndTime());
        }
    }

    @Test
    void testSort_列表元素字段约束必须生效() throws Exception {
        for (String path : List.of("/tasks/reSort", "/taskDetails/reSort", "/taskColumn/reSort")) {
            for (String payload : List.of("[{}]", "[null]", "[]")) {
                mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload))
                        .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
            }
        }
        verifyNoInteractions(tasks, details, columns);
    }
}
