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

/** 目标服务：固定顺序与业务写操作在同一用户锁和事务内保存。 */
@Service
public class GoalServiceImpl extends ServiceImpl<IGoalMapper, GoalEntity> implements IGoalService {

    @Override
    @Transactional
    public GoalEntity createForUser(long userId, GoalEntity entity) {
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
        entity.setPinnedSort(pinned == 1 ? nextFirstSort(userId) : 0);
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public GoalEntity updateForUser(long userId, GoalEntity entity) {
        lockOwner(userId);
        GoalEntity existing = owned(userId, entity.getId());
        applyPin(userId, entity, existing, entity.getIsPinned());
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.update(entity, ownedUpdate(userId, entity.getId()));
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
                .set(GoalEntity::getIsPinned, 0).set(GoalEntity::getPinnedSort, 0)
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
                .orderByAsc(GoalEntity::getPinnedSort).orderByDesc(GoalEntity::getId));
    }

    private void applyPin(long userId, GoalEntity update, GoalEntity existing, Integer requested) {
        int value = requested == null ? existing.getIsPinned() : requested;
        validatePin(value);
        update.setIsPinned(value);
        update.setPinnedSort(value == 0 ? 0 : Integer.valueOf(1).equals(existing.getIsPinned())
                ? existing.getPinnedSort() : nextFirstSort(userId));
    }

    private void validatePin(int value) {
        if (value != 0 && value != 1) throw new IllegalArgumentException("固定状态仅支持 0 或 1");
    }

    private int nextFirstSort(long userId) {
        List<GoalEntity> records = pinned(userId);
        if (records.isEmpty()) return 0;
        int first = records.getFirst().getPinnedSort();
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
                    .set(GoalEntity::getPinnedSort, index)
                    .set(GoalEntity::getUpdateUser, userId).set(GoalEntity::getUpdateTime, now));
        }
    }
}
