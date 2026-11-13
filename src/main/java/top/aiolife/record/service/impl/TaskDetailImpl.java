package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.ITaskDetailMapper;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.pojo.entity.TaskDetailEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 23:24
 */
@Service
@RequiredArgsConstructor
public class TaskDetailImpl extends ServiceImpl<ITaskDetailMapper, TaskDetailEntity> implements ITaskDetail {

    private final ITaskMapper taskMapper;

    @Override
    public Map<Long, Integer> getUnCompletedCount(List<Long> taskIdList, Long userId) {
        if (SysUtil.isEmpty(taskIdList)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        queryWrapper.in(TaskDetailEntity::getTaskId, taskIdList);
        queryWrapper.eq(TaskDetailEntity::getIsCompleted, 0);
        return list(queryWrapper).stream()
                .collect(Collectors.groupingBy(TaskDetailEntity::getTaskId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    @Override
    public List<TaskDetailEntity> listForTask(long userId, long taskId) {
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getTaskId, taskId);
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        queryWrapper.orderByAsc(TaskDetailEntity::getIsCompleted);
        QueryOrder.ascNullsFirst(queryWrapper, TaskDetailEntity::getPriority);
        queryWrapper.orderByAsc(TaskDetailEntity::getSortOrder, TaskDetailEntity::getId);
        return list(queryWrapper);
    }

    @Override
    @Transactional
    public void reSort(long userId, List<TaskDetailEntity> list) {
        for (TaskDetailEntity entity : list) {
            TaskDetailEntity update = new TaskDetailEntity();
            update.setSortOrder(entity.getSortOrder());
            update.fillUpdateCommonField(userId);
            update(update, ownedWrapper(userId, entity.getId()));
        }
    }

    @Override
    @Transactional
    public TaskDetailEntity createForUser(long userId, TaskDetailEntity entity) {
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        save(entity);
        return entity;
    }

    @Override
    @Transactional
    public boolean updateForUser(long userId, TaskDetailEntity entity) {
        entity.setUserId(userId);
        entity.setCreateUser(null);
        entity.setCreateTime(null);
        entity.fillUpdateCommonField(userId);
        // 确保只能更新自己的详情
        return update(entity, ownedWrapper(userId, entity.getId()));
    }

    @Override
    @Transactional
    public void deleteForUser(long userId, long id) {
        remove(ownedWrapper(userId, id));
    }

    @Override
    @Transactional
    public boolean setStarred(long userId, long id, int isStarred) {
        TaskDetailEntity entity = new TaskDetailEntity();
        entity.setIsStarred(isStarred);
        entity.fillUpdateCommonField(userId);
        return update(entity, ownedWrapper(userId, id));
    }

    @Override
    public List<TaskDetailEntity> listWatched(long userId) {
        LambdaQueryWrapper<TaskDetailEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskDetailEntity::getUserId, userId);
        queryWrapper.eq(TaskDetailEntity::getIsStarred, 1);
        queryWrapper.eq(TaskDetailEntity::getIsCompleted, 0);
        QueryOrder.ascNullsFirst(queryWrapper, TaskDetailEntity::getPriority);
        queryWrapper.orderByAsc(TaskDetailEntity::getSortOrder, TaskDetailEntity::getId);
        List<TaskDetailEntity> list = list(queryWrapper);
        if (list.isEmpty()) {
            return list;
        }

        List<Long> taskIds = list.stream().map(TaskDetailEntity::getTaskId)
                .filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return list;
        }
        Map<Long, String> taskNameMap = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                        .in(TaskEntity::getId, taskIds)
                        .eq(TaskEntity::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(TaskEntity::getId, TaskEntity::getContent, (v1, v2) -> v1));
        list.forEach(detail -> detail.setTaskName(taskNameMap.get(detail.getTaskId())));
        return list;
    }

    private LambdaQueryWrapper<TaskDetailEntity> ownedWrapper(long userId, long id) {
        return new LambdaQueryWrapper<TaskDetailEntity>()
                .eq(TaskDetailEntity::getId, id)
                .eq(TaskDetailEntity::getUserId, userId);
    }
}
