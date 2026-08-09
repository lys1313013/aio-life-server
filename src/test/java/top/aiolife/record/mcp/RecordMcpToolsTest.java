package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.api.*;
import top.aiolife.record.mcp.req.TaskDetailSaveMcpReq;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.mcp.req.TimeRecordSaveMcpReq;
import top.aiolife.record.mcp.vo.TaskMcpVO;
import top.aiolife.record.mcp.vo.TimeTrackerCategoryMcpVO;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.mapper.ITaskMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecordMcpTools 单元测试
 * 不依赖数据库，通过动态代理和匿名内部类模拟依赖组件的行为
 */
class RecordMcpToolsTest {

    private RecordMcpTools recordMcpTools;

    private boolean thoughtSaved = false;
    private TimeRecordReq savedTimeRecord = null;
    private TaskDetailEntity savedTaskDetail = null;
    private StpLogic originalStpLogic;

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

        TimeRecordController timeRecordController = new TimeRecordController(null, null, null, null) {
            @Override
            public ApiResponse<List<TimeRecordDateRangeVO>> queryByDateRangeForAI(TimeRecordDateRangeMcpReq req) {
                TimeRecordDateRangeVO vo = new TimeRecordDateRangeVO();
                vo.setId("1");
                return ApiResponse.success(Collections.singletonList(vo));
            }

            @Override
            public ApiResponse<Boolean> save(TimeRecordReq req) {
                savedTimeRecord = req;
                return ApiResponse.success(true);
            }
        };

        ThoughtController thoughtController = new ThoughtController(null, null) {
            @Override
            public ApiResponse<Boolean> save(ThoughtSaveReq req) {
                thoughtSaved = true;
                return ApiResponse.success(true);
            }
        };

        TimeTrackerCategoryController timeTrackerCategoryController = new TimeTrackerCategoryController(null) {
            @Override
            public ApiResponse<List<TimeTrackerCategoryEntity>> list() {
                TimeTrackerCategoryEntity category = new TimeTrackerCategoryEntity();
                category.setId(1L);
                category.setName("Category1");
                return ApiResponse.success(Collections.singletonList(category));
            }
        };

        ITimeRecordService timeRecordService = (ITimeRecordService) java.lang.reflect.Proxy.newProxyInstance(
                ITimeRecordService.class.getClassLoader(),
                new Class[]{ITimeRecordService.class},
                (proxy, method, args) -> {
                    if ("lambdaQuery".equals(method.getName())) {
                        BaseMapper<TimeRecordEntity> dummyMapper = (BaseMapper<TimeRecordEntity>) java.lang.reflect.Proxy.newProxyInstance(
                                BaseMapper.class.getClassLoader(),
                                new Class[]{BaseMapper.class},
                                (mProxy, mMethod, mArgs) -> {
                                    if ("selectList".equals(mMethod.getName()) || "selectOne".equals(mMethod.getName())) {
                                        TimeRecordEntity lastRecord = new TimeRecordEntity();
                                        lastRecord.setEndTime(60);
                                        if ("selectOne".equals(mMethod.getName())) return lastRecord;
                                        return Collections.singletonList(lastRecord);
                                    }
                                    return null;
                                }
                        );
                        return new LambdaQueryChainWrapper<TimeRecordEntity>(dummyMapper);
                    }
                    return null;
                }
        );

        ITaskMapper taskMapper = (ITaskMapper) java.lang.reflect.Proxy.newProxyInstance(
                ITaskMapper.class.getClassLoader(),
                new Class[]{ITaskMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        TaskEntity task = new TaskEntity();
                        task.setId(1L);
                        task.setUserId(1L); // Match StpUtil dummy logic later
                        return task;
                    }
                    return null;
                }
        );

        TaskController taskController = new TaskController(null, null, null) {
            @Override
            public ApiResponse<PageResp<TaskEntity>> query(Long taskId, int get, int pageSize) {
                TaskEntity task = new TaskEntity();
                task.setId(1L);
                task.setContent("Task1");
                PageResp<TaskEntity> pageResp = new PageResp<>();
                pageResp.setItems(Collections.singletonList(task));
                return ApiResponse.success(pageResp);
            }
            
            @Override
            public ITaskMapper getBaseMapper() {
                return taskMapper;
            }
        };

        TaskDetailController taskDetailController = new TaskDetailController(null, null) {
            @Override
            public ApiResponse<List<TaskDetailEntity>> list(Long taskId) {
                TaskDetailEntity detail = new TaskDetailEntity();
                detail.setId(1L);
                detail.setContent("Detail1");
                detail.setIsCompleted(0);
                return ApiResponse.success(Collections.singletonList(detail));
            }

            @Override
            public ApiResponse<TaskDetailEntity> create(TaskDetailEntity entity) {
                savedTaskDetail = entity;
                return ApiResponse.success(entity);
            }
        };

        recordMcpTools = new RecordMcpTools(
                timeRecordController,
                thoughtController,
                timeTrackerCategoryController,
                timeRecordService,
                taskController,
                taskDetailController,
                null
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
        assertEquals(61, savedTimeRecord.getStartTime());
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
        List<TaskMcpVO> result = recordMcpTools.task_list();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Task1", result.get(0).getContent());
        assertEquals(1, result.get(0).getDetails().size());
        assertEquals("Detail1", result.get(0).getDetails().get(0).getContent());
    }

    @Test
    void testTaskDetailSave() {
        // 测试保存任务详情
        TaskDetailSaveMcpReq req = new TaskDetailSaveMcpReq();
        req.setTaskId(1L);
        req.setContent("New Detail");

        boolean result = recordMcpTools.task_detail_save(req);

        assertTrue(result);
        assertNotNull(savedTaskDetail);
        assertEquals("New Detail", savedTaskDetail.getContent());
    }
}
