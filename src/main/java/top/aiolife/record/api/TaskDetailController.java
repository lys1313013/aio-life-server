package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 任务详情控制器
 *
 * @author Lys
 * @date 2026-02-07 23:50
 */
@RestController
@AllArgsConstructor
@RequestMapping("/taskDetails")
public class TaskDetailController {

    private final ITaskDetail taskDetailService;
    private final ITaskService taskService;

    /**
     * 获取任务详情列表
     *
     * @param taskId 任务ID
     * @return 详情列表
     */
    @GetMapping
    public ApiResponse<List<TaskDetailEntity>> list(@RequestParam Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getTaskId, taskId);
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        queryWrapper.orderByAsc(TaskDetailEntity::getIsCompleted);
        queryWrapper.orderByAsc(TaskDetailEntity::getPriority);
        queryWrapper.orderByAsc(TaskDetailEntity::getSort, TaskDetailEntity::getId);
        return ApiResponse.success(taskDetailService.list(queryWrapper));
    }

    /**
     * 拖拽排序
     *
     * @param list 只传id和sort
     */
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@RequestBody List<TaskDetailEntity> list) {
        Long userId = StpUtil.getLoginIdAsLong();
        for (TaskDetailEntity entity : list) {
            LambdaQueryWrapper<TaskDetailEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskDetailEntity::getId, entity.getId());
            wrapper.eq(TaskDetailEntity::getUserId, userId);
            taskDetailService.update(entity, wrapper);
        }
        return ApiResponse.success();
    }

    /**
     * 创建任务详情
     *
     * @param entity 详情实体
     * @return 创建后的实体
     */
    @PostMapping
    public ApiResponse<TaskDetailEntity> create(@RequestBody TaskDetailEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        taskDetailService.save(entity);
        return ApiResponse.success(entity);
    }

    /**
     * 更新任务详情
     *
     * @param entity 详情实体
     * @return 是否成功
     */
    @PutMapping
    public ApiResponse<Boolean> update(@RequestBody TaskDetailEntity entity) {
        Long userId = StpUtil.getLoginIdAsLong();
        entity.fillUpdateCommonField(userId);
        // 确保只能更新自己的详情
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getId, entity.getId());
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        return ApiResponse.success(taskDetailService.update(entity, queryWrapper));
    }

    /**
     * 删除任务详情
     *
     * @param id 详情ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getId, id);
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        taskDetailService.remove(queryWrapper);
        return ApiResponse.success();
    }

    /**
     * 关注任务明细
     *
     * @param id 详情ID
     * @return 是否成功
     */
    @PostMapping("/star/{id}")
    public ApiResponse<Boolean> star(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getId, id);
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        TaskDetailEntity entity = new TaskDetailEntity();
        entity.setIsStarred(1);
        entity.fillUpdateCommonField(userId);
        return ApiResponse.success(taskDetailService.update(entity, queryWrapper));
    }

    /**
     * 取消关注任务明细
     *
     * @param id 详情ID
     * @return 是否成功
     */
    @PostMapping("/unstar/{id}")
    public ApiResponse<Boolean> unstar(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getId, id);
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        TaskDetailEntity entity = new TaskDetailEntity();
        entity.setIsStarred(0);
        entity.fillUpdateCommonField(userId);
        return ApiResponse.success(taskDetailService.update(entity, queryWrapper));
    }

    /**
     * 获取所有关注的明细
     *
     * @return 关注的明细列表
     */
    @GetMapping("/watched")
    public ApiResponse<List<TaskDetailEntity>> getWatched() {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        queryWrapper.eq(TaskDetailEntity::getIsStarred, 1);
        queryWrapper.eq(TaskDetailEntity::getIsCompleted, 0);
        queryWrapper.orderByAsc(TaskDetailEntity::getPriority);
        queryWrapper.orderByAsc(TaskDetailEntity::getSort, TaskDetailEntity::getId);
        List<TaskDetailEntity> list = taskDetailService.list(queryWrapper);
        
        if (!list.isEmpty()) {
            List<Long> taskIds = list.stream().map(TaskDetailEntity::getTaskId)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (!taskIds.isEmpty()) {
                LambdaQueryWrapper<TaskEntity> taskQuery = new LambdaQueryWrapper<>();
                taskQuery.in(TaskEntity::getId, taskIds);
                taskQuery.eq(TaskEntity::getUserId, userId);
                Map<Long, String> taskNameMap = taskService.list(taskQuery).stream()
                        .collect(Collectors.toMap(TaskEntity::getId, TaskEntity::getContent, (v1, v2) -> v1));
                for (TaskDetailEntity detail : list) {
                    detail.setTaskName(taskNameMap.get(detail.getTaskId()));
                }
            }
        }
        
        return ApiResponse.success(list);
    }
}
