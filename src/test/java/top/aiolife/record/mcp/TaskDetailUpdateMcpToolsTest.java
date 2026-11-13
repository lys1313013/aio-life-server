package top.aiolife.record.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import top.aiolife.record.mcp.req.TaskDetailUpdateMcpReq;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.service.ITaskDetail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TaskDetailUpdateMcpToolsTest {

    private ITaskDetail taskDetailService;
    private RecordMcpTools tools;
    private MockedStatic<cn.dev33.satoken.stp.StpUtil> login;

    @BeforeEach
    void setUp() {
        taskDetailService = mock(ITaskDetail.class);
        tools = new RecordMcpTools(null, null, null, null, taskDetailService);
        login = mockStatic(cn.dev33.satoken.stp.StpUtil.class);
        login.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        if (login != null) login.close();
    }

    @Test
    void testUpdate_组合更新保留大整数ID且零值不丢失() {
        when(taskDetailService.updateForUser(anyLong(), any())).thenReturn(true);
        TaskDetailUpdateMcpReq req = request();
        req.setPriority(1);
        req.setIsStarred(0);
        req.setIsCompleted(0);

        assertTrue(tools.task_detail_update(req));

        ArgumentCaptor<TaskDetailEntity> captured = ArgumentCaptor.forClass(TaskDetailEntity.class);
        verify(taskDetailService).updateForUser(anyLong(), captured.capture());
        TaskDetailEntity actual = captured.getValue();
        assertEquals(2112457917496135682L, actual.getId());
        assertEquals(1, actual.getPriority());
        assertEquals(0, actual.getIsStarred());
        assertEquals(0, actual.getIsCompleted());
        assertNull(actual.getContent());
        assertNull(actual.getTaskId());
        assertNull(actual.getSortOrder());
        assertNull(actual.getStartTime());
        assertNull(actual.getEndTime());
    }

    @Test
    void testUpdate_仅完成时不附带其他字段() {
        when(taskDetailService.updateForUser(anyLong(), any())).thenReturn(true);
        assertTrue(tools.task_detail_update(request()));

        ArgumentCaptor<TaskDetailEntity> captured = ArgumentCaptor.forClass(TaskDetailEntity.class);
        verify(taskDetailService).updateForUser(anyLong(), captured.capture());
        assertEquals(1, captured.getValue().getIsCompleted());
        assertNull(captured.getValue().getIsStarred());
        assertNull(captured.getValue().getPriority());
    }

    @Test
    void testUpdate_未修改任何记录时不能报告成功() {
        when(taskDetailService.updateForUser(anyLong(), any())).thenReturn(false);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> tools.task_detail_update(request())).getMessage().contains("无权限"));
    }

    @Test
    void testUpdate_业务失败即使服务抛错也向上报告() {
        when(taskDetailService.updateForUser(anyLong(), any())).thenThrow(new IllegalStateException("更新失败"));
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
        verifyNoInteractions(taskDetailService);
    }

    @Test
    void testUpdate_拒绝空请求及无修改字段() {
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(null));
        TaskDetailUpdateMcpReq req = request();
        req.setIsCompleted(null);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(taskDetailService);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, 21})
    void testUpdate_拒绝非法优先级(int priority) {
        TaskDetailUpdateMcpReq req = request();
        req.setPriority(priority);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        verifyNoInteractions(taskDetailService);
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
        verifyNoInteractions(taskDetailService);
    }

    private TaskDetailUpdateMcpReq request() {
        TaskDetailUpdateMcpReq req = new TaskDetailUpdateMcpReq();
        req.setId("2112457917496135682");
        req.setIsCompleted(1);
        return req;
    }
}
