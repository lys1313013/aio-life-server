package top.aiolife.record.mcp;

import top.aiolife.record.mcp.req.TaskListMcpReq;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.mcp.req.TaskDetailUpdateMcpReq;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "aio.life.neo4j.enabled=false")
class TaskDetailUpdateMcpIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RecordMcpTools tools;

    @Autowired
    private ITaskDetail detailService;

    @Autowired
    private ITaskService taskService;

    @Test
    void testUpdate_仅置顶保留其他字段及创建审计并可查询确认() {
        TaskDetailEntity detail = createDetail(TEST_USER_ID);
        TaskDetailUpdateMcpReq req = request(detail);
        req.setIsStarred(1);
        assertTrue(tools.task_detail_update(req));

        TaskDetailEntity updated = detailService.getById(detail.getId());
        assertEquals(1, updated.getIsStarred());
        assertEquals(detail.getPriority(), updated.getPriority());
        assertEquals(detail.getIsCompleted(), updated.getIsCompleted());
        assertEquals(detail.getContent(), updated.getContent());
        assertEquals(detail.getTaskId(), updated.getTaskId());
        assertEquals(detail.getSortOrder(), updated.getSortOrder());
        assertEquals(detail.getStartTime(), updated.getStartTime());
        assertEquals(detail.getEndTime(), updated.getEndTime());
        assertEquals(detail.getCreateUser(), updated.getCreateUser());
        assertEquals(detail.getCreateTime(), updated.getCreateTime());
        assertEquals(TEST_USER_ID, updated.getUpdateUser());
        assertTrue(updated.getUpdateTime().isAfter(detail.getUpdateTime()));

        var queried = tools.task_list(new TaskListMcpReq()).getItems().stream().filter(task -> task.getId().equals(detail.getTaskId()))
                .flatMap(task -> task.getDetails().stream()).filter(item -> item.getId().equals(detail.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, queried.getIsStarred());
        assertEquals(10, queried.getPriority());
        assertEquals(0, queried.getIsCompleted());
    }

    @Test
    void testUpdate_完成和恢复并取消置顶能写入零值() {
        TaskDetailEntity detail = createDetail(TEST_USER_ID);
        TaskDetailUpdateMcpReq req = request(detail);
        req.setIsCompleted(1);
        req.setIsStarred(1);
        req.setPriority(1);
        assertTrue(tools.task_detail_update(req));
        TaskDetailEntity completed = detailService.getById(detail.getId());
        assertEquals(1, completed.getIsCompleted());
        assertEquals(1, completed.getIsStarred());
        assertEquals(1, completed.getPriority());

        req.setIsCompleted(0);
        req.setIsStarred(0);
        req.setPriority(null);
        assertTrue(tools.task_detail_update(req));
        TaskDetailEntity restored = detailService.getById(detail.getId());
        assertEquals(0, restored.getIsCompleted());
        assertEquals(0, restored.getIsStarred());
        assertEquals(1, restored.getPriority());
    }

    @Test
    void testUpdate_禁止修改他人明细() {
        TaskDetailEntity detail = createDetail(2L);
        TaskDetailUpdateMcpReq req = request(detail);
        req.setIsCompleted(1);
        req.setIsStarred(1);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        TaskDetailEntity unchanged = detailService.getById(detail.getId());
        assertEquals(0, unchanged.getIsCompleted());
        assertEquals(0, unchanged.getIsStarred());
        assertEquals(2L, unchanged.getUserId());
        assertEquals(detail.getUpdateTime(), unchanged.getUpdateTime());
    }

    @Test
    void testUpdate_已删除或不存在时失败且不新增() {
        TaskDetailEntity detail = createDetail(TEST_USER_ID);
        assertTrue(detailService.removeById(detail.getId()));
        long count = detailService.count();
        TaskDetailUpdateMcpReq req = request(detail);
        req.setIsCompleted(1);
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        req.setId(Long.toString(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> tools.task_detail_update(req));
        assertEquals(count, detailService.count());
        assertNull(detailService.getById(detail.getId()));
    }

    private TaskDetailUpdateMcpReq request(TaskDetailEntity detail) {
        TaskDetailUpdateMcpReq req = new TaskDetailUpdateMcpReq();
        req.setId(detail.getId().toString());
        return req;
    }

    private TaskDetailEntity createDetail(Long userId) {
        TaskEntity task = new TaskEntity();
        task.setUserId(userId);
        task.setContent("MCP更新回归测试");
        task.fillCreateCommonField(userId);
        assertTrue(taskService.save(task));

        TaskDetailEntity detail = new TaskDetailEntity();
        detail.setTaskId(task.getId());
        detail.setUserId(userId);
        detail.setContent("保留原明细内容");
        detail.setIsCompleted(0);
        detail.setIsStarred(0);
        detail.setPriority(10);
        detail.setSortOrder(7);
        detail.setStartTime(LocalDateTime.of(2026, 10, 20, 9, 0));
        detail.setEndTime(LocalDateTime.of(2026, 10, 20, 10, 0));
        detail.fillCreateCommonField(userId);
        detail.setCreateTime(LocalDateTime.of(2026, 10, 19, 9, 0));
        detail.setUpdateTime(detail.getCreateTime());
        assertTrue(detailService.save(detail));
        return detail;
    }
}
