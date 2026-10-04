package top.aiolife.membership.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.mapper.MembershipProviderMapper;
import top.aiolife.membership.pojo.entity.MembershipProviderEntity;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.req.MembershipProviderReq;
import top.aiolife.membership.pojo.vo.MembershipProviderVO;

@Service
@RequiredArgsConstructor
public class MembershipProviderService {
    private final MembershipProviderMapper mapper;
    private final IMembershipMapper records;
    private final MembershipIconCatalog icons;

    public List<MembershipProviderVO> list(boolean enabledOnly) {
        return mapper.selectList(new LambdaQueryWrapper<MembershipProviderEntity>()
                .eq(enabledOnly, MembershipProviderEntity::getIsEnabled, 1)
                .orderByAsc(MembershipProviderEntity::getSortOrder, MembershipProviderEntity::getId))
                .stream().map(this::toVO).toList();
    }

    public Map<Long, MembershipProviderEntity> findAll(Collection<Long> ids) {
        List<Long> nonNull = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (nonNull.isEmpty()) return Map.of();
        return mapper.selectBatchIds(nonNull).stream().collect(Collectors.toMap(MembershipProviderEntity::getId, Function.identity()));
    }

    /** 必须在记录保存事务内调用，序列化新增引用与平台停用、删除。 */
    public MembershipProviderEntity lockForRecord(Long id, Long previousId) {
        if (id == null) return null;
        MembershipProviderEntity provider = requireLocked(id);
        if (!Integer.valueOf(1).equals(provider.getIsEnabled()) && !id.equals(previousId)) {
            throw new IllegalArgumentException("该会员平台已停用，请重新选择");
        }
        return provider;
    }

    @Transactional
    public MembershipProviderVO save(long userId, Long id, MembershipProviderReq req) {
        if (id != null) requireLocked(id);
        String iconKey = icons.normalizeKey(req.getIconKey());
        String name = req.getName().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("平台名称不能为空");
        if (mapper.selectCount(new LambdaQueryWrapper<MembershipProviderEntity>()
                .eq(MembershipProviderEntity::getCode, req.getCode())
                .ne(id != null, MembershipProviderEntity::getId, id)) > 0) {
            throw new IllegalArgumentException("平台编码已存在");
        }
        MembershipProviderEntity entity = new MembershipProviderEntity();
        BeanUtil.copyProperties(req, entity);
        entity.setName(name);
        entity.setIconKey(iconKey);
        entity.setId(id);
        if (id == null) {
            entity.fillCreateCommonField(userId);
            mapper.insert(entity);
        } else {
            entity.fillUpdateCommonField(userId);
            mapper.update(entity, new LambdaUpdateWrapper<MembershipProviderEntity>()
                    .eq(MembershipProviderEntity::getId, id).set(iconKey == null, MembershipProviderEntity::getIconKey, null));
        }
        return toVO(entity);
    }

    @Transactional
    public void delete(long userId, Long id) {
        requireLocked(id);
        if (records.selectCount(new LambdaQueryWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getProviderId, id)) > 0) {
            throw new IllegalArgumentException("平台已被会员记录引用，请停用而非删除");
        }
        mapper.update(null, new LambdaUpdateWrapper<MembershipProviderEntity>()
                .eq(MembershipProviderEntity::getId, id)
                .set(MembershipProviderEntity::getIsDeleted, 1)
                .set(MembershipProviderEntity::getUpdateUser, userId)
                .set(MembershipProviderEntity::getUpdateTime, LocalDateTime.now()));
    }

    private MembershipProviderEntity requireLocked(Long id) {
        MembershipProviderEntity provider = mapper.lockById(id);
        if (provider == null) throw new IllegalArgumentException("会员平台不存在");
        return provider;
    }

    private MembershipProviderVO toVO(MembershipProviderEntity entity) {
        return BeanUtil.copyProperties(entity, MembershipProviderVO.class);
    }
}
