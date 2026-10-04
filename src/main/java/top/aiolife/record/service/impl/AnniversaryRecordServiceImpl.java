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
import top.aiolife.record.mapper.IAnniversaryRecordMapper;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.service.IAnniversaryRecordService;

/** 纪念日服务：固定顺序与业务写操作在同一用户锁和事务内保存。 */
@Service
public class AnniversaryRecordServiceImpl extends ServiceImpl<IAnniversaryRecordMapper, AnniversaryRecordEntity> implements IAnniversaryRecordService {

    @Override
    @Transactional
    public AnniversaryRecordEntity createForUser(long userId, AnniversaryRecordEntity entity) {
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
    public AnniversaryRecordEntity updateForUser(long userId, AnniversaryRecordEntity entity) {
        lockOwner(userId);
        AnniversaryRecordEntity existing = owned(userId, entity.getId());
        applyPin(userId, entity, existing, entity.getIsPinned());
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.update(entity, ownedUpdate(userId, entity.getId()));
        return owned(userId, entity.getId());
    }

    @Override
    @Transactional
    public AnniversaryRecordEntity setPinned(long userId, long id, int isPinned) {
        lockOwner(userId);
        AnniversaryRecordEntity existing = owned(userId, id);
        AnniversaryRecordEntity update = new AnniversaryRecordEntity();
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
        List<Long> current = pinned(userId).stream().map(AnniversaryRecordEntity::getId).toList();
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
        baseMapper.update(null, new LambdaUpdateWrapper<AnniversaryRecordEntity>()
                .eq(AnniversaryRecordEntity::getUserId, userId).eq(AnniversaryRecordEntity::getIsDeleted, 0)
                .in(AnniversaryRecordEntity::getId, ids)
                .set(AnniversaryRecordEntity::getIsDeleted, 1)
                .set(AnniversaryRecordEntity::getIsPinned, 0).set(AnniversaryRecordEntity::getPinnedSort, 0)
                .set(AnniversaryRecordEntity::getUpdateUser, userId)
                .set(AnniversaryRecordEntity::getUpdateTime, LocalDateTime.now()));
    }

    private void lockOwner(long userId) {
        if (baseMapper.lockPinOwner(userId) == null) throw new IllegalArgumentException("用户不存在");
    }

    private AnniversaryRecordEntity owned(long userId, Long id) {
        if (id == null) throw new IllegalArgumentException("记录 ID 不能为空");
        AnniversaryRecordEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<AnniversaryRecordEntity>()
                .eq(AnniversaryRecordEntity::getId, id).eq(AnniversaryRecordEntity::getUserId, userId)
                .eq(AnniversaryRecordEntity::getIsDeleted, 0));
        if (entity == null) throw new IllegalArgumentException("记录不存在或无权限");
        return entity;
    }

    private LambdaUpdateWrapper<AnniversaryRecordEntity> ownedUpdate(long userId, long id) {
        return new LambdaUpdateWrapper<AnniversaryRecordEntity>()
                .eq(AnniversaryRecordEntity::getId, id).eq(AnniversaryRecordEntity::getUserId, userId)
                .eq(AnniversaryRecordEntity::getIsDeleted, 0);
    }

    private List<AnniversaryRecordEntity> pinned(long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<AnniversaryRecordEntity>()
                .eq(AnniversaryRecordEntity::getUserId, userId).eq(AnniversaryRecordEntity::getIsDeleted, 0)
                .eq(AnniversaryRecordEntity::getIsPinned, 1)
                .orderByAsc(AnniversaryRecordEntity::getPinnedSort).orderByDesc(AnniversaryRecordEntity::getId));
    }

    private void applyPin(long userId, AnniversaryRecordEntity update, AnniversaryRecordEntity existing, Integer requested) {
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
        List<AnniversaryRecordEntity> records = pinned(userId);
        if (records.isEmpty()) return 0;
        int first = records.getFirst().getPinnedSort();
        if (first == Integer.MIN_VALUE) {
            writeOrder(userId, records.stream().map(AnniversaryRecordEntity::getId).toList());
            return -1;
        }
        return first - 1;
    }

    private void writeOrder(long userId, List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < ids.size(); index++) {
            baseMapper.update(null, ownedUpdate(userId, ids.get(index))
                    .eq(AnniversaryRecordEntity::getIsPinned, 1)
                    .set(AnniversaryRecordEntity::getPinnedSort, index)
                    .set(AnniversaryRecordEntity::getUpdateUser, userId).set(AnniversaryRecordEntity::getUpdateTime, now));
        }
    }
}
