package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

import java.util.List;
import java.util.Map;

/**
 * 任务控制器
 *
 * @author Lys
 * @date 2025/04/12 14:46
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/tasks")
public class TaskController {

    private final ITaskService taskService;

    private final ITaskMapper taskMapper;

    private final ITaskDetail taskDetailService;

    public ITaskMapper getBaseMapper() {
        return taskMapper;
    }

    @GetMapping()
    public ApiResponse<PageResp<TaskEntity>> query(@RequestParam(required = false) Long taskId,
                                                    @RequestParam(defaultValue = "1") int get,
                                                   @RequestParam(defaultValue = "100") int pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TaskEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TaskEntity::getUserId, userId);
        if (taskId != null) {
            lambdaQueryWrapper.eq(TaskEntity::getId, taskId);
        }

        lambdaQueryWrapper.orderByAsc(TaskEntity::getSortOrder);        // 分页
        Page<TaskEntity> page = new Page<>(get, pageSize);
        IPage<TaskEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        List<TaskEntity> records = iPage.getRecords();
        // 获取未完成任务数
        List<Long> taskIdList = records.stream().map(TaskEntity::getId).toList();
        Map<Long, Integer> unCompletedCountMap = taskDetailService.getUnCompletedCount(taskIdList, userId);
        records.forEach(record -> {
            Integer count = unCompletedCountMap.get(record.getId());
            record.setUnCompletedCount(count != null ? count : 0);
        });

        PageResp<TaskEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    /**
     * 插入或更新
     *
     * @param entity
     */
    @PostMapping
    public ApiResponse<TaskEntity> save(@RequestBody TaskEntity entity) {
        entity.setId(null);
        // 获取token
        entity.setUserId(StpUtil.getLoginIdAsLong());
        getBaseMapper().insert(entity);
        return ApiResponse.success(entity);
    }

    /**
     * 更新
     *
     * @param entity
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @RequestBody TaskEntity entity) {
        entity.setId(id);
        entity.setUserId(null);
        LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskEntity::getId, id);
        wrapper.eq(TaskEntity::getUserId, StpUtil.getLoginIdAsLong());
        getBaseMapper().update(entity, wrapper);
        return ApiResponse.success();
    }

    /**
     * 删除
     * @param entity id
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskEntity::getId, id);
        wrapper.eq(TaskEntity::getUserId, StpUtil.getLoginIdAsLong());
        getBaseMapper().delete(wrapper);
        return ApiResponse.success();
    }

    /**
     * 拖拽排序
     *
     * @param list 只传id、columnId和sortOrder
     */
    @PostMapping("/reSort")
    public ApiResponse<Void> reSort(@RequestBody List<TaskEntity> list) {
        Long userId = StpUtil.getLoginIdAsLong();
        for (TaskEntity entity : list) {
            LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskEntity::getId, entity.getId());
            wrapper.eq(TaskEntity::getUserId, userId);
            getBaseMapper().update(entity, wrapper);
        }
        return ApiResponse.success();
    }
}
