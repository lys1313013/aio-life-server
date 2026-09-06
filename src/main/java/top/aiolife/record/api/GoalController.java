package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.service.IGoalService;

import java.time.LocalDateTime;
import java.util.List;

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

    private final IGoalMapper goalMapper;
    private final IGoalService goalService;

    @GetMapping
    public ApiResponse<List<GoalEntity>> queryGoals(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
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
        queryWrapper.orderByDesc(GoalEntity::getCreateTime);
        return ApiResponse.success(goalService.list(queryWrapper));
    }

    @PostMapping
    public ApiResponse<GoalEntity> createGoal(@RequestBody GoalEntity goalEntity) {
        long userId = StpUtil.getLoginIdAsLong();
        goalEntity.setUserId(userId);
        goalEntity.setIsDeleted(0);
        goalEntity.setCreateTime(LocalDateTime.now());
        goalEntity.setUpdateTime(LocalDateTime.now());
        goalEntity.setCreateUser(userId);
        goalEntity.setUpdateUser(userId);
        goalMapper.insert(goalEntity);
        return ApiResponse.success(goalEntity);
    }

    @PutMapping
    public ApiResponse<GoalEntity> updateGoal(@RequestBody GoalEntity goalEntity) {
        long userId = StpUtil.getLoginIdAsLong();
        goalEntity.setUpdateTime(LocalDateTime.now());
        goalEntity.setUpdateUser(userId);
        goalEntity.setUserId(null); // 防止修改所属用户
        
        LambdaUpdateWrapper<GoalEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GoalEntity::getId, goalEntity.getId());
        updateWrapper.eq(GoalEntity::getUserId, userId);
        goalService.update(goalEntity, updateWrapper);
        
        return ApiResponse.success(goalEntity);
    }

    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteGoals(@RequestBody CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<GoalEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GoalEntity::getUserId, userId);
        updateWrapper.in(GoalEntity::getId, commonReq.getIdList());
        updateWrapper.set(GoalEntity::getIsDeleted, 1);
        updateWrapper.set(GoalEntity::getUpdateTime, LocalDateTime.now());
        updateWrapper.set(GoalEntity::getUpdateUser, userId);
        goalService.update(null, updateWrapper);
        return ApiResponse.success();
    }
}
