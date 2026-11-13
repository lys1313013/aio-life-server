package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mcp.req.TaskDetailSaveMcpReq;
import top.aiolife.record.mcp.req.TaskListMcpReq;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.mcp.req.TimeRecordSaveMcpReq;
import top.aiolife.record.mcp.vo.TaskMcpVO;
import top.aiolife.record.mcp.vo.TimeTrackerCategoryMcpVO;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.ThoughtCreationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RecordMcpTools 单元测试
 * 不依赖数据库，通过 mock 5 个 Service 模拟依赖组件的行为
 */
class RecordMcpToolsTest {

    private RecordMcpTools recordMcpTools;

    private boolean thoughtSaved = false;
    private TimeRecordReq savedTimeRecord = null;
    private TaskDetailEntity savedTaskDetail = null;
    private StpLogic originalStpLogic;
    private int requestedTaskPage;
    private int requestedTaskPageSize;

    @BeforeEach
    void setUp() {
        originalStpLogic = StpUtil.getStpLogic();
        StpUtil.setStpLogic(new StpLogic("login") {
            @Override
            public long getLoginIdAsLong() {
                return 1L;
            }

            @Override
            public Object getLoginIdDefaultNull() {
                return 1L;
            }

            @Override
            public void checkLogin() {
                // 单元测试固定为用户 1 已登录
            }
        });

        thoughtSaved = false;
        savedTimeRecord = null;
        savedTaskDetail = null;

        var timeRecordService = mock(ITimeRecordService.class);
        when(timeRecordService.queryByDateRangeForAI(anyLong(), any(), any())).thenAnswer(call -> {
            TimeRecordDateRangeVO vo = new TimeRecordDateRangeVO();
            vo.setId("1");
            return Collections.singletonList(vo);
        });
        when(timeRecordService.findLatestEndMinute(anyLong(), any())).thenReturn(60);
        when(timeRecordService.saveTimeRecord(anyLong(), any())).thenAnswer(call -> {
            savedTimeRecord = call.getArgument(1);
            return "2099999999999999999";
        });

        var thoughtCreationService = mock(ThoughtCreationService.class);
        doAnswer(call -> {
            thoughtSaved = true;
            return null;
        }).when(thoughtCreationService).create(anyLong(), any(ThoughtSaveReq.class));

        var timeTrackerCategoryService = mock(ITimeTrackerCategoryService.class);
        when(timeTrackerCategoryService.listUserVisibleCategories(anyLong())).thenAnswer(call -> {
            TimeTrackerCategoryEntity category = new TimeTrackerCategoryEntity();
            category.setId(1L);
            category.setName("Category1");
            return Collections.singletonList(category);
        });

        var taskService = mock(ITaskService.class);
        when(taskService.pageForUser(anyLong(), any(), anyInt(), anyInt())).thenAnswer(call -> {
            requestedTaskPage = call.getArgument(2);
            requestedTaskPageSize = call.getArgument(3);
            if (requestedTaskPageSize > 100) {
                throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
            }
            TaskEntity task = new TaskEntity();
            task.setId(1L);
            task.setContent("Task1");
            PageResp<TaskEntity> pageResp = new PageResp<>();
            pageResp.setItems(Collections.singletonList(task));
            pageResp.setTotal(205L);
            return pageResp;
        });
        when(taskService.getOwned(anyLong(), anyLong())).thenAnswer(call -> {
            TaskEntity task = new TaskEntity();
            task.setId(1L);
            task.setUserId(1L);
            return task;
        });

        var taskDetailService = mock(ITaskDetail.class);
        when(taskDetailService.listForTask(anyLong(), anyLong())).thenAnswer(call -> {
            TaskDetailEntity detail = new TaskDetailEntity();
            detail.setId(1L);
            detail.setContent("Detail1");
            detail.setIsCompleted(0);
            detail.setPriority(10);
            detail.setIsStarred(1);
            return Collections.singletonList(detail);
        });
        when(taskDetailService.createForUser(anyLong(), any(TaskDetailEntity.class))).thenAnswer(call -> {
            savedTaskDetail = call.getArgument(1);
            return savedTaskDetail;
        });

        recordMcpTools = new RecordMcpTools(
                thoughtCreationService,
                timeRecordService,
                timeTrackerCategoryService,
                taskService,
                taskDetailService
        );
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
    }

    @Test
    void testTimeRecordQueryByDateRange() {
        // 测试按日期范围查询时间记录
        TimeRecordDateRangeMcpReq req = new TimeRecordDateRangeMcpReq();
        List<TimeRecordDateRangeVO> result = recordMcpTools.time_record_queryByDateRange(req);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getId());
    }

    @Test
    void testTimeRecordSave() {
        // 测试保存时间记录
        TimeRecordSaveMcpReq req = new TimeRecordSaveMcpReq();
        req.setDate(LocalDate.now());

        String result = recordMcpTools.time_record_save(req);

        assertTrue(result.contains("保存成功"));
        assertNotNull(savedTimeRecord);
        assertEquals(61, savedTimeRecord.getStartMinute());
    }

    @Test
    void historicalTimeRequiresExplicitEndpointsAndPreservesInclusiveMinutes() {
        var request = new TimeRecordSaveMcpReq();
        request.setDate(LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> recordMcpTools.time_record_save(request));
        request.setStartMinute(600);
        assertThrows(IllegalArgumentException.class, () -> recordMcpTools.time_record_save(request));
        request.setEndMinute(659);
        recordMcpTools.time_record_save(request);
        assertEquals(600, savedTimeRecord.getStartMinute());
        assertEquals(659, savedTimeRecord.getEndMinute());
        assertEquals(LocalDate.of(2026, 1, 1), savedTimeRecord.getDate());
        request.setEndMinute(599);
        assertThrows(IllegalArgumentException.class, () -> recordMcpTools.time_record_save(request));
    }

    @Test
    void testThoughtSave() {
        // 测试保存想法
        ThoughtSaveReq req = new ThoughtSaveReq();
        boolean result = recordMcpTools.thought_save(req);

        assertTrue(result);
        assertTrue(thoughtSaved);
    }

    @Test
    void testTimeTrackerCategoryList() {
        // 测试查询时间分类列表
        List<TimeTrackerCategoryMcpVO> result = recordMcpTools.time_tracker_category_list();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Category1", result.get(0).getName());
    }

    @Test
    void testTaskList() {
        // 测试查询任务列表
        List<TaskMcpVO> result = recordMcpTools.task_list(new TaskListMcpReq()).getItems();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Task1", result.get(0).getContent());
        assertEquals(1, result.get(0).getDetails().size());
        assertEquals("Detail1", result.get(0).getDetails().get(0).getContent());
        assertEquals(10, result.get(0).getDetails().get(0).getPriority());
        assertEquals(1, result.get(0).getDetails().get(0).getIsStarred());
        assertEquals(1, requestedTaskPage);
        assertEquals(10, requestedTaskPageSize);
    }

    @Test
    void taskListPropagatesPageAndPreservesTotal() {
        TaskListMcpReq request = new TaskListMcpReq();
        request.setPage(2);
        request.setPageSize(100);

        PageResp<TaskMcpVO> result = recordMcpTools.task_list(request);

        assertEquals(2, requestedTaskPage);
        assertEquals(100, requestedTaskPageSize);
        assertEquals(205L, result.getTotal());
        assertEquals("Detail1", result.getItems().get(0).getDetails().get(0).getContent());
        request.setPageSize(101);
        assertThrows(IllegalArgumentException.class, () -> recordMcpTools.task_list(request));
    }

    @Test
    void testTaskDetailSave() {
        // 测试保存任务详情
        TaskDetailSaveMcpReq req = new TaskDetailSaveMcpReq();
        req.setTaskId("1");
        req.setContent("New Detail");

        boolean result = recordMcpTools.task_detail_save(req);

        assertTrue(result);
        assertNotNull(savedTaskDetail);
        assertEquals("New Detail", savedTaskDetail.getContent());
    }
}
