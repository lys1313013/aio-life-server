package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.entity.GoalEntity;
import top.aiolife.record.service.IGoalService;
import top.aiolife.record.service.GoalProgressEvaluator;
import lombok.RequiredArgsConstructor;

/** 目标服务：固定顺序与业务写操作在同一用户锁和事务内保存。 */
@Service
@RequiredArgsConstructor
public class GoalServiceImpl extends ServiceImpl<IGoalMapper, GoalEntity> implements IGoalService {

    private final GoalProgressEvaluator progress;

    @Override
    @Transactional
    public GoalEntity createForUser(long userId, GoalEntity entity) {
        progress.validate(entity);
        entity.setProgressVersion(0L);
        if ("graphql".equals(entity.getProgressMode())) entity.setCurrentValue(null);
        lockOwner(userId);
        entity.setUserId(userId);
        entity.setIsDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(entity.getCreateTime());
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        int pinned = entity.getIsPinned() == null ? 0 : entity.getIsPinned();
        validatePin(pinned);
        entity.setIsPinned(pinned);
        entity.setPinnedSortOrder(pinned == 1 ? nextFirstSort(userId) : 0);
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public GoalEntity updateForUser(long userId, GoalEntity entity) {
        lockOwner(userId);
        GoalEntity existing = owned(userId, entity.getId());
        // 部分更新保留已保存配置；显式切回手动时清除查询，不留隐式旧配置。
        if (entity.getProgressMode() == null) entity.setProgressMode(existing.getProgressMode());
        if (entity.getTargetOperator() == null) entity.setTargetOperator(existing.getTargetOperator());
        if (entity.getTargetValue() == null) entity.setTargetValue(existing.getTargetValue());
        if (entity.getProgressQuery() == null) entity.setProgressQuery(existing.getProgressQuery());
        if (entity.getProgressVariables() == null) entity.setProgressVariables(existing.getProgressVariables());
        if (entity.getProgressVariableBindings() == null) entity.setProgressVariableBindings(existing.getProgressVariableBindings());
        if (entity.getProgressValuePath() == null) entity.setProgressValuePath(existing.getProgressValuePath());
        if (entity.getStartDate() == null) entity.setStartDate(existing.getStartDate());
        if (entity.getEndDate() == null) entity.setEndDate(existing.getEndDate());
        progress.validate(entity);
        entity.setProgressVersion(existing.getProgressVersion() + 1);
        applyPin(userId, entity, existing, entity.getIsPinned());
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        var update = ownedUpdate(userId, entity.getId()).set(GoalEntity::getProgressRefreshId, null);
        if ("graphql".equals(entity.getProgressMode())) {
            entity.setCurrentValue(null);
            boolean changed = !"graphql".equals(existing.getProgressMode())
                    || !Objects.equals(entity.getProgressQuery(), existing.getProgressQuery())
                    || !progress.sameJson(entity.getProgressVariables(), existing.getProgressVariables())
                    || !progress.sameJson(entity.getProgressVariableBindings(), existing.getProgressVariableBindings())
                    || !Objects.equals(entity.getProgressValuePath(), existing.getProgressValuePath())
                    || !Objects.equals(entity.getStartDate(), existing.getStartDate())
                    || !Objects.equals(entity.getEndDate(), existing.getEndDate());
            if (changed) update.set(GoalEntity::getCurrentValue, null).set(GoalEntity::getProgressCheckedAt, null)
                    .set(GoalEntity::getProgressError, "尚未计算");
        } else {
            update.set(GoalEntity::getProgressQuery, null).set(GoalEntity::getProgressVariables, null)
                    .set(GoalEntity::getProgressVariableBindings, null).set(GoalEntity::getProgressValuePath, null)
                    .set(GoalEntity::getProgressCheckedAt, null).set(GoalEntity::getProgressError, null);
            entity.setProgressQuery(null); entity.setProgressVariables(null);
            entity.setProgressVariableBindings(null); entity.setProgressValuePath(null);
        }
        baseMapper.update(entity, update);
        return owned(userId, entity.getId());
    }

    @Override
    @Transactional
    public GoalEntity setPinned(long userId, long id, int isPinned) {
        lockOwner(userId);
        GoalEntity existing = owned(userId, id);
        GoalEntity update = new GoalEntity();
        applyPin(userId, update, existing, isPinned);
        update.setUpdateUser(userId);
        update.setUpdateTime(LocalDateTime.now());
        baseMapper.update(update, ownedUpdate(userId, id));
        return owned(userId, id);
    }

    @Override
    @Transactional
    public void reorderPinned(long userId, List<Long> ids) {
        lockOwner(userId);
        if (ids == null || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("固定记录 ID 不能为空或重复");
        }
        List<Long> current = pinned(userId).stream().map(GoalEntity::getId).toList();
        if (!new HashSet<>(current).equals(new HashSet<>(ids))) {
            throw new IllegalArgumentException("固定列表已变化，请刷新后重试");
        }
        writeOrder(userId, ids);
    }

    @Override
    @Transactional
    public void deleteForUser(long userId, List<String> ids) {
        lockOwner(userId);
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("请选择要删除的记录");
        baseMapper.update(null, new LambdaUpdateWrapper<GoalEntity>()
                .eq(GoalEntity::getUserId, userId).eq(GoalEntity::getIsDeleted, 0)
                .in(GoalEntity::getId, ids)
                .set(GoalEntity::getIsDeleted, 1)
                .set(GoalEntity::getIsPinned, 0).set(GoalEntity::getPinnedSortOrder, 0)
                .set(GoalEntity::getUpdateUser, userId)
                .set(GoalEntity::getUpdateTime, LocalDateTime.now()));
    }

    private void lockOwner(long userId) {
        if (baseMapper.lockPinOwner(userId) == null) throw new IllegalArgumentException("用户不存在");
    }

    private GoalEntity owned(long userId, Long id) {
        if (id == null) throw new IllegalArgumentException("记录 ID 不能为空");
        GoalEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<GoalEntity>()
                .eq(GoalEntity::getId, id).eq(GoalEntity::getUserId, userId)
                .eq(GoalEntity::getIsDeleted, 0));
        if (entity == null) throw new IllegalArgumentException("记录不存在或无权限");
        return entity;
    }

    private LambdaUpdateWrapper<GoalEntity> ownedUpdate(long userId, long id) {
        return new LambdaUpdateWrapper<GoalEntity>()
                .eq(GoalEntity::getId, id).eq(GoalEntity::getUserId, userId)
                .eq(GoalEntity::getIsDeleted, 0);
    }

    private List<GoalEntity> pinned(long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<GoalEntity>()
                .eq(GoalEntity::getUserId, userId).eq(GoalEntity::getIsDeleted, 0)
                .eq(GoalEntity::getIsPinned, 1)
                .orderByAsc(GoalEntity::getPinnedSortOrder).orderByDesc(GoalEntity::getId));
    }

    private void applyPin(long userId, GoalEntity update, GoalEntity existing, Integer requested) {
        int value = requested == null ? existing.getIsPinned() : requested;
        validatePin(value);
        update.setIsPinned(value);
        update.setPinnedSortOrder(value == 0 ? 0 : Integer.valueOf(1).equals(existing.getIsPinned())
                ? existing.getPinnedSortOrder() : nextFirstSort(userId));
    }

    private void validatePin(int value) {
        if (value != 0 && value != 1) throw new IllegalArgumentException("固定状态仅支持 0 或 1");
    }

    private int nextFirstSort(long userId) {
        List<GoalEntity> records = pinned(userId);
        if (records.isEmpty()) return 0;
        int first = records.getFirst().getPinnedSortOrder();
        if (first == Integer.MIN_VALUE) {
            writeOrder(userId, records.stream().map(GoalEntity::getId).toList());
            return -1;
        }
        return first - 1;
    }

    private void writeOrder(long userId, List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < ids.size(); index++) {
            baseMapper.update(null, ownedUpdate(userId, ids.get(index))
                    .eq(GoalEntity::getIsPinned, 1)
                    .set(GoalEntity::getPinnedSortOrder, index)
                    .set(GoalEntity::getUpdateUser, userId).set(GoalEntity::getUpdateTime, now));
        }
    }
}
