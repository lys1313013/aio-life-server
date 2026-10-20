package top.aiolife.record.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.api.TaskDetailController;
import top.aiolife.record.mcp.req.TaskDetailUpdateMcpReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskDetailUpdateMcpToolsTest {

    private TaskDetailController controller;
    private RecordMcpTools tools;

    @BeforeEach
    void setUp() {
        controller = mock(TaskDetailController.class);
        tools = new RecordMcpTools(null, null, null, null, null, controller, null);
    }

    @Test
    void testUpdate_组合更新保留大整数ID且零值不丢失() {
        when(controller.update(any())).thenReturn(ApiResponse.success(true));
        TaskDetailUpdateMcpReq req = request();
        req.setPriority(1);
        req.setIsStarred(0);
        req.setIsCompleted(0);

        assertTrue(tools.task_detail_update(req));

        ArgumentCaptor<TaskDetailUpdateReq> captured = ArgumentCaptor.forClass(TaskDetailUpdateReq.class);
        verify(controller).update(captured.capture());
        TaskDetailUpdateReq actual = captured.getValue();
        assertEquals(2112457917496135682L, actual.getId());
        assertEquals(1, actual.getPriority());
        assertEquals(0, actual.getIsStarred());
        assertEquals(0, actual.getIsCompleted());
        assertNull(actual.getContent());
        assertNull(actual.getTaskId());
        assertNull(actual.getSort());
        assertNull(actual.getStartTime());
        assertNull(actual.getEndTime());
    }

    @Test
    void testUpdate_仅完成时不附带其他字段() {
        when(controller.update(any())).thenReturn(ApiResponse.success(true));
        assertTrue(tools.task_detail_update(request()));

        ArgumentCaptor<TaskDetailUpdateReq> captured = ArgumentCaptor.forClass(TaskDetailUpdateReq.class);
        verify(controller).update(captured.capture());
        assertEquals(1, captured.getValue().getIsCompleted());
        assertNull(captured.getValue().getIsStarred());
        assertNull(captured.getValue().getPriority());
    }

    @Test
    void testUpdate_未修改任何记录时不能报告成功() {
        when(controller.update(any())).thenReturn(ApiResponse.success(false));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> tools.task_detail_update(request())).getMessage().contains("无权限"));
    }

    @Test
    void testUpdate_业务失败即使data为true也报错() {
        when(controller.update(any())).thenReturn(ApiResponse.error(113000, "更新失败", true));
        assertEquals("更新失败", assertThrows(IllegalStateException.class,
                () -> tools.task_detail_update(request())).getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "1.5", "abc", " 1", "9223372036854775808"})
    void testUpdate_拒绝非法ID(String id) {
        TaskDetailUpdateMcpReq req = request();
        req.setId(id);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(controller);
    }

    @Test
    void testUpdate_拒绝空请求及无修改字段() {
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(null));
        TaskDetailUpdateMcpReq req = request();
        req.setIsCompleted(null);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(controller);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, 21})
    void testUpdate_拒绝非法优先级(int priority) {
        TaskDetailUpdateMcpReq req = request();
        req.setPriority(priority);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(controller);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 10})
    void testUpdate_拒绝非法状态(int state) {
        TaskDetailUpdateMcpReq req = request();
        req.setIsCompleted(state);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        req.setIsCompleted(1);
        req.setIsStarred(state);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(controller);
    }

    private TaskDetailUpdateMcpReq request() {
        TaskDetailUpdateMcpReq req = new TaskDetailUpdateMcpReq();
        req.setId("2112457917496135682");
        req.setIsCompleted(1);
        return req;
    }
}
