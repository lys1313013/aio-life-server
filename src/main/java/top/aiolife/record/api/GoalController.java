package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.tags.Tag;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentToken;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.GoalCreateReq;
import top.aiolife.record.pojo.req.GoalProgressPreviewReq;
import top.aiolife.record.pojo.req.GoalProgressRefreshReq;
import top.aiolife.record.pojo.req.GoalUpdateReq;
import top.aiolife.record.pojo.req.HomePinReq;
import top.aiolife.record.pojo.req.HomePinnedOrderReq;
import top.aiolife.record.pojo.vo.GoalProgressPreviewVO;
import top.aiolife.record.pojo.vo.GoalVO;
import top.aiolife.record.service.GoalProgressEvaluator;
import top.aiolife.record.service.GoalProgressService;
import top.aiolife.record.service.IGoalService;

/**
 * 目标管理控制器
 *
 * @author Lys
 * @date 2026-04-05
 */
@Tag(name = "目标 计划")
@RestController
@AllArgsConstructor
@RequestMapping("/goals")
public class GoalController {

    private final IGoalService goalService;
    private final GoalProgressEvaluator progressEvaluator;
    private final GoalProgressService progressService;

    @GetMapping
    @Operation(summary = "查询目标列表", description = "返回目标列表，支持目标类型、进度状态及关键词筛选，按创建时间倒序。")
    public ApiResponse<List<GoalVO>> queryGoals(
            @CurrentUserId String userId,
            @Parameter(description = "目标周期类型", schema = @Schema(implementation = GoalTypeEnum.class))
            @RequestParam(required = false) Integer type,
            @Parameter(description = "进度状态", schema = @Schema(allowableValues = {"not_started", "in_progress", "completed", "on_hold"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "匹配目标标题、描述或标签")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer isPinned) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVOList(
                goalService.listForUser(IdCodec.decode(userId), type, status, keyword, isPinned)));
    }

    @PostMapping
    @Operation(summary = "新增目标", description = "用户归属和审计信息由服务端设置；parentId 可关联父目标。响应返回创建后的目标及 ID。")
    public ApiResponse<GoalVO> createGoal(@CurrentUserId String userId,
                                          @Valid @RequestBody GoalCreateReq goalEntityReq) {
        GoalEntity goalEntity = RecordApiConvertor.INSTANCE.fromGoalCreateReq(goalEntityReq);
        goalEntity = goalService.createForUser(IdCodec.decode(userId), goalEntity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(goalEntity));
    }

    @PutMapping
    @Operation(summary = "更新目标", description = "请求体携带目标 id，只更新当前用户拥有的目标；用户归属不可修改。")
    public ApiResponse<GoalVO> updateGoal(@CurrentUserId String userId,
                                          @Valid @RequestBody GoalUpdateReq goalEntityReq) {
        GoalEntity goalEntity = RecordApiConvertor.INSTANCE.fromGoalUpdateReq(goalEntityReq);
        goalEntity = goalService.updateForUser(IdCodec.decode(userId), goalEntity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(goalEntity));
    }

    @PostMapping("/progress/preview")
    @Operation(summary = "预览 GraphQL 目标进度", description = "使用当前用户凭据执行查询，返回数值、达标结果与绑定后的变量，不保存目标。")
    public ApiResponse<GoalProgressPreviewVO> previewProgress(@CurrentToken String token,
                                                              @Valid @RequestBody GoalProgressPreviewReq request) {
        return ApiResponse.success(progressEvaluator.evaluate(
                RecordApiConvertor.INSTANCE.fromGoalCreateReq(request), token));
    }

    @PostMapping("/progress/refresh")
    @Operation(summary = "刷新目标查询进度", description = "单批最多十个目标；每项查询失败保留成功快照并返回 progressError，配置变化时丢弃过期结果。")
    public ApiResponse<List<GoalVO>> refreshProgress(@CurrentUserId String userId, @CurrentToken String token,
                                                     @Valid @RequestBody GoalProgressRefreshReq request) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVOList(
                progressService.refresh(IdCodec.decode(userId), request.getIds(), token)));
    }

    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除目标", description = "请求体 idList 为目标 ID 列表，仅逻辑删除当前用户拥有的目标。")
    public ApiResponse<Void> deleteGoals(@CurrentUserId String userId,
                                         @Valid @RequestBody CommonReq commonReq) {
        goalService.deleteForUser(IdCodec.decode(userId), commonReq.getIdList());
        return ApiResponse.success();
    }

    @Operation(summary = "设置目标置顶状态")
    @PutMapping("/{id}/pin")
    public ApiResponse<GoalVO> setPinned(@CurrentUserId String userId, @PathVariable String id,
                                         @Valid @RequestBody HomePinReq request) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(
                goalService.setPinned(IdCodec.decode(userId), IdCodec.decode(id), request.getIsPinned())));
    }

    @Operation(summary = "调整置顶目标排序")
    @PutMapping("/pinned-order")
    public ApiResponse<Void> reorderPinned(@CurrentUserId String userId,
                                           @Valid @RequestBody HomePinnedOrderReq request) {
        goalService.reorderPinned(IdCodec.decode(userId), request.getIds());
        return ApiResponse.success();
    }
}
