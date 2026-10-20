package top.aiolife.membership.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.pojo.entity.MembershipProviderEntity;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.req.MembershipReq;
import top.aiolife.membership.pojo.vo.MembershipStatsVO;

/** 记录所有权、平台引用和持久化必须处于同一事务。 */
@Service
@RequiredArgsConstructor
public class MembershipRecordWriteService {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_EXPIRING = "expiring";
    private static final String STATUS_EXPIRED = "expired";
    private static final long DEFAULT_REMIND_DAYS = 7;
    private static final String DEFAULT_BILLING_CYCLE = "month";
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final Set<String> BILLING_CYCLES = Set.of(
            "week", "two_weeks", "month", "quarter", "half_year", "year"
    );

    private final IMembershipMapper mapper;
    private final MembershipProviderService providers;

    public List<MembershipRecordEntity> list(long userId) {
        return mapper.selectList(new LambdaQueryWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getUserId, userId)
                .orderByAsc(MembershipRecordEntity::getExpiryDate));
    }

    public MembershipRecordEntity getOwned(long userId, Long id) {
        return mapper.selectOne(new LambdaQueryWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getId, id)
                .eq(MembershipRecordEntity::getUserId, userId));
    }

    public String status(MembershipRecordEntity entity, LocalDate today) {
        return calcStatus(entity, today);
    }

    public MembershipStatsVO stats(long userId) {
        List<MembershipRecordEntity> entities = mapper.selectList(new LambdaQueryWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getUserId, userId));
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        MembershipStatsVO vo = new MembershipStatsVO();
        vo.setActiveCount(Math.toIntExact(entities.stream().filter(e -> STATUS_ACTIVE.equals(calcStatus(e, today))).count()));
        vo.setExpiringCount(Math.toIntExact(entities.stream().filter(e -> STATUS_EXPIRING.equals(calcStatus(e, today))).count()));
        vo.setExpiredCount(Math.toIntExact(entities.stream().filter(e -> STATUS_EXPIRED.equals(calcStatus(e, today))).count()));
        vo.setExpiringThisMonthCount(Math.toIntExact(entities.stream()
                .filter(e -> !e.getExpiryDate().isBefore(today) && !e.getExpiryDate().isAfter(monthEnd)).count()));
        vo.setMonthlyAmount(entities.stream()
                .filter(e -> !e.getExpiryDate().isBefore(today))
                .filter(e -> e.getStartDate() == null || !e.getStartDate().isAfter(today))
                .map(MembershipRecordEntity::getMonthlyAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return vo;
    }

    public void delete(long userId, Long id) {
        mapper.update(null, new LambdaUpdateWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getId, id)
                .eq(MembershipRecordEntity::getUserId, userId)
                .set(MembershipRecordEntity::getIsDeleted, 1)
                .set(MembershipRecordEntity::getUpdateUser, userId)
                .set(MembershipRecordEntity::getUpdateTime, LocalDateTime.now()));
    }

    public void normalizeCost(MembershipReq req) {
        String billingCycle = req.getBillingCycle();
        if (billingCycle == null || billingCycle.isBlank()) {
            billingCycle = DEFAULT_BILLING_CYCLE;
            req.setBillingCycle(billingCycle);
        }
        if (!BILLING_CYCLES.contains(billingCycle)) throw new IllegalArgumentException("不支持的计费周期");
        if (req.getPrice() != null && req.getPrice().signum() < 0) throw new IllegalArgumentException("支付金额不能小于0");
        if (req.getMonthlyAmount() != null && req.getMonthlyAmount().signum() < 0) throw new IllegalArgumentException("月均金额不能小于0");
        if (req.getMonthlyAmount() == null) req.setMonthlyAmount(calculateMonthlyAmount(req.getPrice(), billingCycle));
        else req.setMonthlyAmount(req.getMonthlyAmount().setScale(2, RoundingMode.HALF_UP));
    }

    private String calcStatus(MembershipRecordEntity entity, LocalDate today) {
        if (entity.getExpiryDate().isBefore(today)) return STATUS_EXPIRED;
        if (!entity.getExpiryDate().isAfter(today.plusDays(DEFAULT_REMIND_DAYS))) return STATUS_EXPIRING;
        return STATUS_ACTIVE;
    }

    private BigDecimal calculateMonthlyAmount(BigDecimal price, String billingCycle) {
        if (price == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyAmount = switch (billingCycle) {
            case "week" -> price.multiply(DAYS_PER_MONTH).divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            case "two_weeks" -> price.multiply(DAYS_PER_MONTH).divide(BigDecimal.valueOf(14), 2, RoundingMode.HALF_UP);
            case "quarter" -> price.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            case "half_year" -> price.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
            case "year" -> price.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> price.setScale(2, RoundingMode.HALF_UP);
        };
        return monthlyAmount.setScale(2, RoundingMode.HALF_UP);
    }

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
