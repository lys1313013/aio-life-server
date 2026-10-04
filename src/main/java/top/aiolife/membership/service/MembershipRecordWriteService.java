package top.aiolife.membership.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.pojo.entity.MembershipProviderEntity;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.req.MembershipReq;

/** 记录所有权、平台引用和持久化必须处于同一事务。 */
@Service
@RequiredArgsConstructor
public class MembershipRecordWriteService {
    private final IMembershipMapper mapper;
    private final MembershipProviderService providers;

    @Transactional
    public MembershipRecordEntity create(long userId, MembershipReq req) {
        MembershipProviderEntity provider = providers.lockForRecord(req.getProviderId(), null);
        MembershipRecordEntity entity = BeanUtil.copyProperties(req, MembershipRecordEntity.class);
        applyPlatform(entity, provider);
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        mapper.insert(entity);
        return entity;
    }

    @Transactional
    public MembershipRecordEntity update(long userId, MembershipReq req) {
        MembershipRecordEntity previous = mapper.lockOwned(req.getId(), userId);
        if (previous == null) throw new IllegalArgumentException("会员记录不存在");
        Long providerId = req.hasProviderId() ? req.getProviderId() : previous.getProviderId();
        MembershipProviderEntity provider = providers.lockForRecord(providerId, previous.getProviderId());
        MembershipRecordEntity entity = BeanUtil.copyProperties(req, MembershipRecordEntity.class);
        entity.setProviderId(providerId);
        if (entity.getCategory() == null || entity.getCategory().isBlank()) entity.setCategory(previous.getCategory());
        applyPlatform(entity, provider);
        entity.fillUpdateCommonField(userId);
        // 显式 null 可解除关联；省略 providerId 保持兼容旧客户端。
        mapper.update(entity, new LambdaUpdateWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getId, previous.getId())
                .eq(MembershipRecordEntity::getUserId, userId)
                .set(providerId == null, MembershipRecordEntity::getProviderId, null));
        return mapper.selectById(previous.getId());
    }

    private void applyPlatform(MembershipRecordEntity entity, MembershipProviderEntity provider) {
        if (provider != null) {
            entity.setProvider(provider.getName());
            if (entity.getCategory() == null || entity.getCategory().isBlank()) entity.setCategory(provider.getCategory());
        }
    }
}
