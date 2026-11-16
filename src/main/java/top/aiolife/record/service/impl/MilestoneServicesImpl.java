package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.record.mapper.IMilestoneMapper;
import top.aiolife.record.pojo.entity.MilestoneEntity;
import top.aiolife.record.service.IMilestoneService;

/**
 * 里程碑服务实现
 *
 * @author Lys
 * @date 2026-02-07 16:44
 */
@Service
public class MilestoneServicesImpl extends ServiceImpl<IMilestoneMapper, MilestoneEntity>
        implements IMilestoneService {

    @Override
    public List<MilestoneEntity> listForUser(long userId) {
        LambdaQueryWrapper<MilestoneEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MilestoneEntity::getUserId, userId);
        QueryOrder.descNullsLast(wrapper, MilestoneEntity::getCreateTime, MilestoneEntity::getId);
        return list(wrapper);
    }

    @Override
    @Transactional
    public MilestoneEntity createForUser(long userId, MilestoneEntity entity) {
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public MilestoneEntity updateForUser(long userId, MilestoneEntity entity, boolean endDateProvided) {
        entity.fillUpdateCommonField(userId);
        entity.setUserId(null);
        LambdaUpdateWrapper<MilestoneEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MilestoneEntity::getId, entity.getId());
        wrapper.eq(MilestoneEntity::getUserId, userId);
        // 省略保留，显式 null 清空；有值时由实体更新，避免同一列重复赋值。
        wrapper.set(endDateProvided && entity.getEndDate() == null, MilestoneEntity::getEndDate, null);
        update(entity, wrapper);
        return entity;
    }

    @Override
    @Transactional
    public void deleteForUser(long userId, List<String> ids) {
        LambdaUpdateWrapper<MilestoneEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MilestoneEntity::getUserId, userId);
        wrapper.in(MilestoneEntity::getId, ids);
        wrapper.set(MilestoneEntity::getIsDeleted, 1);
        wrapper.set(MilestoneEntity::getUpdateTime, LocalDateTime.now());
        wrapper.set(MilestoneEntity::getUpdateUser, userId);
        update(null, wrapper);
    }
}
