package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.ITaskColumnMapper;
import top.aiolife.record.pojo.entity.TaskColumnEntity;
import top.aiolife.record.service.ITaskColumnService;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/12 20:19
 */
@Service
@RequiredArgsConstructor
public class TaskColumnServiceImpl extends ServiceImpl<ITaskColumnMapper, TaskColumnEntity> implements ITaskColumnService {

    @Override
    public void updateBatchById(List<TaskColumnEntity> list) {
        super.updateBatchById(list);
    }

    @Override
    public PageResp<TaskColumnEntity> pageForUser(long userId, int page, int pageSize) {
        PaginationRequest.validate(page, pageSize, PaginationRequest.MAX_PAGE_SIZE);
        LambdaQueryWrapper<TaskColumnEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskColumnEntity::getUserId, userId);
        queryWrapper.eq(TaskColumnEntity::getIsDeleted, StatusConst.NO_DELETE);
        queryWrapper.orderByAsc(TaskColumnEntity::getSortOrder, TaskColumnEntity::getId);
        IPage<TaskColumnEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), queryWrapper);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    @Transactional
    public TaskColumnEntity createForUser(long userId, TaskColumnEntity entity) {
        // 查询当前最大的 sort_order
        TaskColumnEntity last = baseMapper.selectOne(new LambdaQueryWrapper<TaskColumnEntity>()
                .eq(TaskColumnEntity::getUserId, userId)
                .orderByDesc(TaskColumnEntity::getSortOrder)
                .last("limit 1"));
        int maxOrder = last != null && last.getSortOrder() != null ? last.getSortOrder() : 0;
        entity.setSortOrder(maxOrder + 1);
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        baseMapper.insertOrUpdate(entity);
        return entity;
    }

    @Override
    @Transactional
    public void updateForUser(long userId, long id, TaskColumnEntity entity) {
        entity.setId(id);
        entity.setUserId(userId);
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
    public void reSort(long userId, List<TaskColumnEntity> list) {
        for (TaskColumnEntity entity : list) {
            TaskColumnEntity update = new TaskColumnEntity();
            update.setSortOrder(entity.getSortOrder());
            update.fillUpdateCommonField(userId);
            baseMapper.update(update, ownedWrapper(userId, entity.getId()));
        }
    }

    private LambdaQueryWrapper<TaskColumnEntity> ownedWrapper(long userId, long id) {
        return new LambdaQueryWrapper<TaskColumnEntity>()
                .eq(TaskColumnEntity::getId, id)
                .eq(TaskColumnEntity::getUserId, userId);
    }
}
