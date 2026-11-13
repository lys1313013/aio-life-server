package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.query.TaskPageQuery;
import top.aiolife.record.pojo.req.TaskCreateReq;
import top.aiolife.record.pojo.req.TaskSortReq;
import top.aiolife.record.pojo.req.TaskUpdateReq;
import top.aiolife.record.pojo.vo.TaskVO;
import top.aiolife.record.service.ITaskService;

/**
 * 任务控制器
 *
 * @author Lys
 * @date 2025/04/12 14:46
 */
@Tag(name = "待办 主任务 任务列表")
@RestController
@AllArgsConstructor
@RequestMapping("/tasks")
public class TaskController {

    private final ITaskService taskService;

    @GetMapping
    @Operation(summary = "分页查询待办主任务")
    public ApiResponse<PageResp<TaskVO>> queryPage(@CurrentUserId String userId,
                                                   @QueryParams TaskPageQuery query) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskVOPage(
                taskService.pageForUser(IdCodec.decode(userId), query.getTaskId(),
                        query.getPage(), query.getPageSize())));
    }

    @PostMapping
    @Operation(summary = "新增待办主任务")
    public ApiResponse<TaskVO> save(@CurrentUserId String userId,
                                    @Valid @RequestBody TaskCreateReq entityReq) {
        TaskEntity entity = RecordApiConvertor.INSTANCE.fromTaskCreateReq(entityReq);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskVO(
                taskService.createForUser(IdCodec.decode(userId), entity)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新待办主任务")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable("id") String id,
                                       @Valid @RequestBody TaskUpdateReq entityReq) {
        TaskEntity entity = RecordApiConvertor.INSTANCE.fromTaskUpdateReq(entityReq);
        taskService.updateForUser(IdCodec.decode(userId), IdCodec.decode(id), entity);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除待办主任务")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable("id") String id) {
        taskService.deleteForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @PostMapping("/reSort")
    @Operation(summary = "调整待办主任务排序")
    public ApiResponse<Void> reSort(@CurrentUserId String userId,
                                    @Valid @NotEmpty @RequestBody List<@NotNull @Valid TaskSortReq> requests) {
        List<TaskEntity> list = requests.stream().map(RecordApiConvertor.INSTANCE::fromTaskSortReq).toList();
        taskService.reSort(IdCodec.decode(userId), list);
        return ApiResponse.success();
    }
}
