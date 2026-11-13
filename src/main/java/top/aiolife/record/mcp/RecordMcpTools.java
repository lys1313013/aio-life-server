package top.aiolife.record.mcp;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.PageResp;
import top.aiolife.mcp.annotation.McpToolProvider;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mcp.req.TaskDetailSaveMcpReq;
import top.aiolife.record.mcp.req.TaskDetailUpdateMcpReq;
import top.aiolife.record.mcp.req.TaskListMcpReq;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.mcp.req.TimeRecordSaveMcpReq;
import top.aiolife.record.mcp.vo.TaskDetailMcpVO;
import top.aiolife.record.mcp.vo.TaskMcpVO;
import top.aiolife.record.mcp.vo.TimeTrackerCategoryMcpVO;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.req.TaskDetailCreateReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.ThoughtCreationService;

@McpToolProvider
@RequiredArgsConstructor
public class RecordMcpTools {

    private final ThoughtCreationService thoughtCreationService;
    private final ITimeRecordService timeRecordService;
    private final ITimeTrackerCategoryService timeTrackerCategoryService;
    private final ITaskService taskService;
    private final ITaskDetail taskDetailService;

    @Tool("查询指定日期范围内的所有时间记录，包含仅有运动名称和运动次数的运动明细")
    public List<TimeRecordDateRangeVO> time_record_queryByDateRange(TimeRecordDateRangeMcpReq req) {
        return timeRecordService.queryByDateRangeForAI(
                StpUtil.getLoginIdAsLong(), req.getStartDate(), req.getEndDate());
    }

    @Tool("新增时迹；省略起止分钟时追加今天末条记录之后至当前时刻。补记历史日期或指定时段须同时提供 startMinute/endMinute（0–1439，闭区间）")
    public String time_record_save(TimeRecordSaveMcpReq req) {
        TimeRecordReq actualReq = new TimeRecordReq();
        BeanUtil.copyProperties(req, actualReq);

        long userId = StpUtil.getLoginIdAsLong();
        LocalDate date = req.getDate() != null ? req.getDate() : LocalDate.now();

        if ((req.getStartMinute() == null) != (req.getEndMinute() == null)) {
            throw new IllegalArgumentException("startMinute 与 endMinute 必须同时提供");
        }
        if (req.getStartMinute() == null && !date.equals(LocalDate.now())) {
            throw new IllegalArgumentException("非今天的记录必须提供 startMinute/endMinute");
        }
        // 显式时间直接保存，省略时按今天的最后一段追加。
        int startMinute;
        int endMinute;
        if (req.getStartMinute() != null) {
            startMinute = req.getStartMinute();
            endMinute = req.getEndMinute();
        } else {
            // 开始时间自动从数据库中查询，最后一条 + 1
            Integer lastEndMinute = timeRecordService.findLatestEndMinute(userId, date);
            startMinute = lastEndMinute != null ? lastEndMinute + 1 : 0;

            // 结束时间为当前时间
            LocalTime now = LocalTime.now();
            endMinute = now.getHour() * 60 + now.getMinute();
        }
        if (startMinute < 0 || endMinute > 1439 || startMinute > endMinute) {
            throw new IllegalArgumentException("时间范围无效或今天已无可追加时段；起止须在 0–1439 且结束不早于开始");
        }

        actualReq.setStartMinute(startMinute);
        actualReq.setEndMinute(endMinute);
        if (actualReq.getDate() == null) {
            actualReq.setDate(date);
        }

        timeRecordService.saveTimeRecord(userId, actualReq);

        // 格式化时间并返回给大模型
        String startTimeStr = String.format("%02d:%02d", startMinute / 60, startMinute % 60);
        String endTimeStr = String.format("%02d:%02d", endMinute / 60, endMinute % 60);
        return String.format("保存成功！开始时间：%s，结束时间：%s，持续时长：%d分钟",
                startTimeStr, endTimeStr, endMinute - startMinute + 1);
    }

    @Tool("保存一条想法，并可附带多个关联事件")
    public boolean thought_save(ThoughtSaveReq req) {
        thoughtCreationService.create(StpUtil.getLoginIdAsLong(), req);
        return true;
    }

    @Tool("查询用户的所有时迹分类（含合并的公共分类）")
    public List<TimeTrackerCategoryMcpVO> time_tracker_category_list() {
        var items = RecordApiConvertor.INSTANCE.toTimeTrackerCategoryVOList(
                timeTrackerCategoryService.listUserVisibleCategories(StpUtil.getLoginIdAsLong()));
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(c -> new TimeTrackerCategoryMcpVO(c.getId(), items.stream()
                        .filter(parent -> parent.getId().equals(c.getParentId()))
                        .map(parent -> parent.getName() + " / " + c.getName())
                        .findFirst().orElse(c.getName())))
                .toList();
    }

    @Tool("分页查询待办主任务及明细，用于取得真实 ID；返回 items/total，根据 total 继续翻页")
    public PageResp<TaskMcpVO> task_list(TaskListMcpReq req) {
        int page = req.getPage() == null ? 1 : req.getPage();
        int pageSize = req.getPageSize() == null ? 10 : req.getPageSize();
        long userId = StpUtil.getLoginIdAsLong();
        var result = taskService.pageForUser(userId, null, page, pageSize);
        List<TaskEntity> items = result.getItems() == null ? List.of() : result.getItems();
        List<TaskMcpVO> records = items.stream()
                .map(t -> {
                    var detailEntities = taskDetailService.listForTask(userId, t.getId());
                    List<TaskDetailMcpVO> details = detailEntities == null ? List.of() : detailEntities.stream()
                            .map(d -> new TaskDetailMcpVO(d.getId(), d.getContent(), d.getIsCompleted(),
                                    d.getPriority(), d.getIsStarred()))
                            .toList();
                    return new TaskMcpVO(t.getId(), t.getContent(), details);
                })
                .toList();
        return PageResp.of(records, result.getTotal());
    }

    @Tool("录入任务明细")
    public boolean task_detail_save(TaskDetailSaveMcpReq req) {
        if (req.getTaskId() == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        long userId = StpUtil.getLoginIdAsLong();
        long taskId = IdCodec.decode(req.getTaskId());
        taskService.getOwned(userId, taskId);

        TaskDetailCreateReq entity = new TaskDetailCreateReq();
        entity.setTaskId(taskId);
        entity.setContent(req.getContent());
        // 设置默认值，避免数据库报错或逻辑异常
        entity.setIsCompleted(0);
        entity.setIsStarred(req.getIsStarred() != null ? req.getIsStarred() : 0);
        entity.setPriority(req.getPriority() != null ? req.getPriority() : 10);
        entity.setSortOrder(0);
        taskDetailService.createForUser(userId, RecordApiConvertor.INSTANCE.fromTaskDetailCreateReq(entity));
        return true;
    }

    @Tool("修改已有任务明细的完成状态、星标置顶或优先级；id 必填（通过 task_list 获取），其他字段至少传一个。仅更新非 null 字段，0 也会写入；只能修改当前用户的明细，失败会报错，不会新增待办")
    public boolean task_detail_update(TaskDetailUpdateMcpReq req) {
        if (req == null || req.getId() == null || !req.getId().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("任务明细ID必须为正整数字符串");
        }
        Long id;
        try {
            id = Long.valueOf(req.getId());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("任务明细ID超出有效范围");
        }
        if (req.getPriority() == null && req.getIsStarred() == null && req.getIsCompleted() == null) {
            throw new IllegalArgumentException("priority、isStarred 与 isCompleted 至少传一个");
        }
        if (req.getPriority() != null && !List.of(1, 10, 20).contains(req.getPriority())) {
            throw new IllegalArgumentException("priority 仅支持 1（高）、10（中）、20（低）");
        }
        if (req.getIsStarred() != null && req.getIsStarred() != 0 && req.getIsStarred() != 1) {
            throw new IllegalArgumentException("isStarred 仅支持 0 或 1");
        }
        if (req.getIsCompleted() != null && req.getIsCompleted() != 0 && req.getIsCompleted() != 1) {
            throw new IllegalArgumentException("isCompleted 仅支持 0 或 1");
        }

        TaskDetailUpdateReq update = new TaskDetailUpdateReq();
        update.setId(id);
        update.setPriority(req.getPriority());
        update.setIsStarred(req.getIsStarred());
        update.setIsCompleted(req.getIsCompleted());
        boolean updated = taskDetailService.updateForUser(
                StpUtil.getLoginIdAsLong(), RecordApiConvertor.INSTANCE.fromTaskDetailUpdateReq(update));
        if (!updated) {
            throw new IllegalArgumentException("任务明细不存在、已删除或无权限修改");
        }
        return true;
    }
}
