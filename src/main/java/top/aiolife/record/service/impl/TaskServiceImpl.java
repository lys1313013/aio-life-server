package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/12 21:01
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<ITaskMapper, TaskEntity> implements ITaskService {

    private final ITaskDetail taskDetailService;

    @Override
    public void updateBatchById(List<TaskEntity> list) {
        super.updateBatchById(list);
    }

    @Override
    public PageResp<TaskEntity> pageForUser(long userId, Long taskId, int page, int pageSize) {
        PaginationRequest.validate(page, pageSize, PaginationRequest.MAX_PAGE_SIZE);
        LambdaQueryWrapper<TaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskEntity::getUserId, userId);
        if (taskId != null) {
            queryWrapper.eq(TaskEntity::getId, taskId);
        }
        QueryOrder.ascNullsFirst(queryWrapper, TaskEntity::getSortOrder, TaskEntity::getId);
        IPage<TaskEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), queryWrapper);
        List<TaskEntity> records = result.getRecords();
        // 获取未完成任务数
        List<Long> taskIdList = records.stream().map(TaskEntity::getId).toList();
        Map<Long, Integer> unCompletedCountMap = taskDetailService.getUnCompletedCount(taskIdList, userId);
        records.forEach(record -> record.setUnCompletedCount(unCompletedCountMap.getOrDefault(record.getId(), 0)));
        return PageResp.of(records, result.getTotal());
    }

    @Override
    @Transactional
    public TaskEntity createForUser(long userId, TaskEntity entity) {
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public void updateForUser(long userId, long id, TaskEntity entity) {
        entity.setId(id);
        entity.setUserId(null);
        entity.fillUpdateCommonField(userId);
        baseMapper.update(entity, ownedWrapper(userId, id));
    }

    @Override
    @Transactional
    public void deleteForUser(long userId, long id) {
        baseMapper.delete(ownedWrapper(userId, id));
    }

    @Override
    @Transactional
    public void reSort(long userId, List<TaskEntity> list) {
        for (TaskEntity entity : list) {
            TaskEntity update = new TaskEntity();
            update.setSortOrder(entity.getSortOrder());
            update.setColumnId(entity.getColumnId());
            update.fillUpdateCommonField(userId);
            baseMapper.update(update, ownedWrapper(userId, entity.getId()));
        }
    }

    @Override
    public TaskEntity getOwned(long userId, long id) {
        TaskEntity entity = baseMapper.selectOne(ownedWrapper(userId, id));
        if (entity == null) {
            throw new IllegalArgumentException("任务不存在或无权限访问该任务");
        }
        return entity;
    }

    private LambdaQueryWrapper<TaskEntity> ownedWrapper(long userId, long id) {
        return new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getId, id)
                .eq(TaskEntity::getUserId, userId);
    }
}
