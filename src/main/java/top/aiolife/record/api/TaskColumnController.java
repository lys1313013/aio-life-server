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
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.TaskColumnEntity;
import top.aiolife.record.pojo.query.TaskColumnQuery;
import top.aiolife.record.pojo.req.TaskColumnCreateReq;
import top.aiolife.record.pojo.req.TaskColumnSortReq;
import top.aiolife.record.pojo.req.TaskColumnUpdateReq;
import top.aiolife.record.pojo.vo.TaskColumnVO;
import top.aiolife.record.service.ITaskColumnService;

/**
 * 任务栏控制器
 *
 * @author Lys
 * @date 2025/04/12 14:36
 */
@Tag(name = "待办 分组")
@RestController
@AllArgsConstructor
@RequestMapping("/taskColumn")
public class TaskColumnController {

    private final ITaskColumnService taskColumnService;

    @Operation(summary = "查询待办分组")
    @GetMapping("/query")
    public ApiResponse<PageResp<TaskColumnVO>> query(
            @CurrentUserId String userId,
            @QueryParams CommonQuery<TaskColumnQuery> requestQuery) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskColumnVOPage(
                taskColumnService.pageForUser(IdCodec.decode(userId),
                        requestQuery.getPage(), requestQuery.getPageSize())));
    }

    @Operation(summary = "新增待办分组")
    @PostMapping
    public ApiResponse<TaskColumnVO> save(@CurrentUserId String userId,
                                          @Valid @RequestBody TaskColumnCreateReq entityReq) {
        TaskColumnEntity entity = RecordApiConvertor.INSTANCE.fromTaskColumnCreateReq(entityReq);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTaskColumnVO(
                taskColumnService.createForUser(IdCodec.decode(userId), entity)));
    }

    @Operation(summary = "更新待办分组")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable("id") String id,
                                       @Valid @RequestBody TaskColumnUpdateReq entityReq) {
        TaskColumnEntity entity = RecordApiConvertor.INSTANCE.fromTaskColumnUpdateReq(entityReq);
        taskColumnService.updateForUser(IdCodec.decode(userId), IdCodec.decode(id), entity);
        return ApiResponse.success();
    }

    @Operation(summary = "删除待办分组")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable("id") String id) {
        taskColumnService.deleteForUser(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @Operation(summary = "调整待办分组排序")
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@CurrentUserId String userId,
                                    @Valid @NotEmpty @RequestBody List<@NotNull @Valid TaskColumnSortReq> requests) {
        List<TaskColumnEntity> list = requests.stream()
                .map(RecordApiConvertor.INSTANCE::fromTaskColumnSortReq).toList();
        taskColumnService.reSort(IdCodec.decode(userId), list);
        return ApiResponse.success();
    }
}
