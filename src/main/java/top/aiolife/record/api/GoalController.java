package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.GoalCreateReq;
import top.aiolife.record.pojo.req.GoalUpdateReq;
import top.aiolife.record.pojo.req.HomePinReq;
import top.aiolife.record.pojo.req.HomePinnedOrderReq;
import top.aiolife.record.pojo.vo.GoalVO;
import top.aiolife.record.service.IGoalService;

/**
 * 目标管理控制器
 *
 * @author Lys
 * @date 2026-04-05
 */
@RestController
@AllArgsConstructor
@RequestMapping("/goals")
public class GoalController {

    private final IGoalService goalService;

    @GetMapping
    @Operation(summary = "查询当前用户的目标", description = "返回目标列表，支持目标类型、进度状态及关键词筛选，按创建时间倒序。")
    public ApiResponse<List<GoalVO>> queryGoals(
            @Parameter(description = "目标类型：1=年度、2=月度、3=日目标", schema = @Schema(allowableValues = {"1", "2", "3"}))
            @RequestParam(required = false) Integer type,
            @Parameter(description = "进度状态", schema = @Schema(allowableValues = {"not_started", "in_progress", "completed", "on_hold"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "匹配目标标题、描述或标签")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer isPinned) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<GoalEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GoalEntity::getUserId, userId);
        queryWrapper.eq(GoalEntity::getIsDeleted, 0);
        if (type != null) {
            queryWrapper.eq(GoalEntity::getType, type);
        }
        if (status != null && !status.isBlank()) {
            queryWrapper.eq(GoalEntity::getStatus, ProgressStatusEnum.fromCode(status));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper ->
                wrapper.like(GoalEntity::getTitle, keyword)
                       .or()
                       .like(GoalEntity::getDescription, keyword)
                       .or()
                       .like(GoalEntity::getTags, keyword)
            );
        }
        if (isPinned != null) {
            if (isPinned != 0 && isPinned != 1) throw new IllegalArgumentException("固定状态仅支持 0 或 1");
            queryWrapper.eq(GoalEntity::getIsPinned, isPinned);
        }
        if (Integer.valueOf(1).equals(isPinned)) {
            queryWrapper.orderByAsc(GoalEntity::getPinnedSort).orderByDesc(GoalEntity::getId);
        } else {
            queryWrapper.orderByDesc(GoalEntity::getCreateTime).orderByDesc(GoalEntity::getId);
        }
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVOList(goalService.list(queryWrapper)));
    }

    @PostMapping
    @Operation(summary = "创建目标", description = "用户归属和审计信息由服务端设置；parentId 可关联父目标。响应返回创建后的目标及 ID。")
    public ApiResponse<GoalVO> createGoal(@Valid @RequestBody GoalCreateReq goalEntityReq) {
        GoalEntity goalEntity = RecordApiConvertor.INSTANCE.fromGoalCreateReq(goalEntityReq);
        long userId = StpUtil.getLoginIdAsLong();
        goalEntity = goalService.createForUser(userId, goalEntity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(goalEntity));
    }

    @PutMapping
    @Operation(summary = "更新目标", description = "请求体携带目标 id，只更新当前用户拥有的目标；用户归属不可修改。")
    public ApiResponse<GoalVO> updateGoal(@Valid @RequestBody GoalUpdateReq goalEntityReq) {
        GoalEntity goalEntity = RecordApiConvertor.INSTANCE.fromGoalUpdateReq(goalEntityReq);
        long userId = StpUtil.getLoginIdAsLong();
        goalEntity = goalService.updateForUser(userId, goalEntity);

        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(goalEntity));
    }

    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除目标", description = "请求体 idList 为目标 ID 列表，仅逻辑删除当前用户拥有的目标。")
    public ApiResponse<Void> deleteGoals(@RequestBody CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        goalService.deleteForUser(userId, commonReq.getIdList());
        return ApiResponse.success();
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<GoalVO> setPinned(@PathVariable Long id, @Valid @RequestBody HomePinReq request) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toGoalVO(
                goalService.setPinned(StpUtil.getLoginIdAsLong(), id, request.getIsPinned())));
    }

    @PutMapping("/pinned-order")
    public ApiResponse<Void> reorderPinned(@Valid @RequestBody HomePinnedOrderReq request) {
        goalService.reorderPinned(StpUtil.getLoginIdAsLong(), request.getIds());
        return ApiResponse.success();
    }
}
