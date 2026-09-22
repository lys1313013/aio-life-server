package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskDetailController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class TaskDetailControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TaskDetailController taskDetailController;

    @Autowired
    private ITaskDetail taskDetailService;

    @Autowired
    private ITaskService taskService;

    @Test
    void testGetWatched_获取关注的明细() {
        var response = taskDetailController.getWatched();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testGetWatched_只回填当前用户任务名称() {
        TaskEntity ownTask = new TaskEntity();
        ownTask.setUserId(TEST_USER_ID);
        ownTask.setContent("自己的任务");
        ownTask.fillCreateCommonField(TEST_USER_ID);
        taskService.save(ownTask);

        TaskEntity otherTask = new TaskEntity();
        otherTask.setUserId(2L);
        otherTask.setContent("其他用户的任务");
        otherTask.fillCreateCommonField(2L);
        taskService.save(otherTask);

        TaskDetailEntity ownDetail = watchedDetail(ownTask.getId());
        TaskDetailEntity mismatchedDetail = watchedDetail(otherTask.getId());

        var watched = assertSuccessWithData(taskDetailController.getWatched()).stream()
                .filter(detail -> ownDetail.getId().equals(detail.getId())
                        || mismatchedDetail.getId().equals(detail.getId()))
                .toList();

        assertEquals(2, watched.size());
        assertEquals("自己的任务", watched.stream()
                .filter(detail -> ownDetail.getId().equals(detail.getId()))
                .findFirst().orElseThrow().getTaskName());
        assertNull(watched.stream()
                .filter(detail -> mismatchedDetail.getId().equals(detail.getId()))
                .findFirst().orElseThrow().getTaskName());
    }

    private TaskDetailEntity watchedDetail(Long taskId) {
        TaskDetailEntity detail = new TaskDetailEntity();
        detail.setTaskId(taskId);
        detail.setUserId(TEST_USER_ID);
        detail.setContent("关注明细");
        detail.setIsCompleted(0);
        detail.setIsStarred(1);
        detail.setSort(0);
        detail.setPriority(20);
        detail.fillCreateCommonField(TEST_USER_ID);
        taskDetailService.save(detail);
        return detail;
    }
}
