package top.aiolife.record.api;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.IReadRecordService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 最小 HTTP 入参必须与公开 Schema 描述相符，不访问业务库。 */
class AiRequestContractTest {
    @Test
    void 新增时迹补今天但拒绝缺少端点更新可仅改标题() throws Exception {
        var service = mock(ITimeRecordService.class);
        when(service.saveTimeRecord(anyLong(), any())).thenAnswer(call -> {
            TimeRecordReq request = call.getArgument(1);
            if (request.getDate() == null) request.setDate(LocalDate.now());
            return "9007199254740993";
        });
        var controller = new TimeRecordController(service);
        var mvc = mvc(controller);
        try (var login = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            login.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsString).thenReturn("1");
            mvc.perform(post("/timeRecord").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startMinute\":600,\"endMinute\":659}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("9007199254740993"));
            var captured = ArgumentCaptor.forClass(TimeRecordReq.class);
            verify(service).saveTimeRecord(eq(1L), captured.capture());
            assertEquals(LocalDate.now(), captured.getValue().getDate());
            for (String body : new String[]{"{}", "{\"startMinute\":600}", "{\"startMinute\":0,\"endMinute\":1440}"}) {
                mvc.perform(post("/timeRecord").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
            mvc.perform(put("/timeRecord/9007199254740993").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"只改标题\"}")).andExpect(status().isOk());
            verify(service).updateTimeRecord(eq(1L), captured.capture());
            assertEquals("9007199254740993", captured.getValue().getId());
            assertNull(captured.getValue().getStartMinute());
            assertNull(captured.getValue().getEndMinute());
        }
    }

    private org.springframework.test.web.servlet.MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new top.aiolife.core.web.CurrentUserIdArgumentResolver(),
                        new top.aiolife.core.web.CurrentTokenArgumentResolver())
                .build();
    }

    @Test
    void 阅读最小新增校验名称类型并返回精确ID() throws Exception {
        var service = mock(IReadRecordService.class);
        when(service.saveRecord(any())).thenReturn(9007199254740993L);
        var mvc = MockMvcBuilders.standaloneSetup(new ReadRecordController(service)).build();
        mvc.perform(post("/read-record").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"模拟书籍\",\"type\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("9007199254740993"));
        for (String body : new String[]{"{}", "{\"title\":\"书名\"}", "{\"title\":\"书名\",\"type\":3}"}) {
            mvc.perform(post("/read-record").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, times(1)).saveRecord(any());
    }
    @Test
    void 待办优先级拒绝枚举之外的数字() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TaskDetailController(mock(top.aiolife.record.service.ITaskDetail.class))).build();
        mvc.perform(post("/taskDetails").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"9007199254740993\",\"content\":\"待办\",\"priority\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/taskDetails").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"9007199254740993\",\"priority\":2}"))
                .andExpect(status().isBadRequest());
    }
}
