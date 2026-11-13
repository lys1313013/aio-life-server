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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.req.TaskDetailCreateReq;
import top.aiolife.record.pojo.req.TaskDetailSortReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.vo.TaskDetailVO;
import top.aiolife.record.service.ITaskDetail;

/**
 * 任务详情控制器
 *
 * @author Lys
 * @date 2026-02-07 23:50
 */
@Tag(name = "待办 任务明细 子任务")
@RestController
@AllArgsConstructor
@RequestMapping("/taskDetails")
public class TaskDetailController {

    private final ITaskDetail taskDetailService;

    /**
     * 获取任务详情列表
     *
     * @param taskId 任务ID
     * @return 详情列表
     */
    @Operation(summary = "查询待办子任务")
    @GetMapping
    public ApiResponse<List<TaskDetailVO>> list(@CurrentUserId String userId, @RequestParam String taskId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskDetailVOList(
                taskDetailService.listForTask(IdCodec.decode(userId), IdCodec.decode(taskId))));
    }

    /**
     * 拖拽排序
     *
     * @param list 只传id和sort
     */
    @Operation(summary = "调整待办子任务排序")
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@CurrentUserId String userId,
                                    @Valid @NotEmpty @RequestBody List<@NotNull @Valid TaskDetailSortReq> requests) {
        List<TaskDetailEntity> list = requests.stream()
                .map(RecordApiConvertor.INSTANCE::fromTaskDetailSortReq).toList();
        taskDetailService.reSort(IdCodec.decode(userId), list);
        return ApiResponse.success();
    }

    /**
     * 创建任务详情
     *
     * @param entity 详情实体
     * @return 创建后的实体
     */
    @Operation(summary = "新增待办子任务")
    @PostMapping
    public ApiResponse<TaskDetailVO> create(@CurrentUserId String userId,
                                            @Valid @RequestBody TaskDetailCreateReq entityReq) {
        TaskDetailEntity entity = RecordApiConvertor.INSTANCE.fromTaskDetailCreateReq(entityReq);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskDetailVO(
                taskDetailService.createForUser(IdCodec.decode(userId), entity)));
    }

    /**
     * 更新任务详情
     *
     * @param entity 详情实体
     * @return 是否成功
     */
    @Operation(summary = "更新待办子任务")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody TaskDetailUpdateReq entityReq) {
        TaskDetailEntity entity = RecordApiConvertor.INSTANCE.fromTaskDetailUpdateReq(entityReq);
        return ApiResponse.success(taskDetailService.updateForUser(IdCodec.decode(userId), entity));
    }

    /**
     * 删除任务详情
     *
     * @param id 详情ID
     * @return 空响应
     */
    @Operation(summary = "删除待办子任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        taskDetailService.deleteForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    /**
     * 关注任务明细
     *
     * @param id 详情ID
     * @return 是否成功
     */
    @Operation(summary = "关注待办子任务")
    @PostMapping("/star/{id}")
    public ApiResponse<Boolean> star(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(
                taskDetailService.setStarred(IdCodec.decode(userId), IdCodec.decode(id), 1));
    }

    /**
     * 取消关注任务明细
     *
     * @param id 详情ID
     * @return 是否成功
     */
    @Operation(summary = "取消关注待办子任务")
    @PostMapping("/unstar/{id}")
    public ApiResponse<Boolean> unstar(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(
                taskDetailService.setStarred(IdCodec.decode(userId), IdCodec.decode(id), 0));
    }

    /**
     * 获取所有关注的明细
     *
     * @return 关注的明细列表
     */
    @Operation(summary = "查询已关注的待办子任务")
    @GetMapping("/watched")
    public ApiResponse<List<TaskDetailVO>> getWatched(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskDetailVOList(
                taskDetailService.listWatched(IdCodec.decode(userId))));
    }
}
