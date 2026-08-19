package top.aiolife.membership.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.req.MembershipReq;
import top.aiolife.membership.pojo.vo.MembershipStatsVO;
import top.aiolife.membership.pojo.vo.MembershipVO;
import top.aiolife.membership.service.IMembershipService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 会员记录控制器
 *
 * @author Lys
 * @date 2026/08/06
 */
@RestController
@AllArgsConstructor
@RequestMapping("/membership")
public class MembershipController {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_EXPIRING = "expiring";
    private static final String STATUS_EXPIRED = "expired";
    private static final long DEFAULT_REMIND_DAYS = 7;
    private static final String DEFAULT_BILLING_CYCLE = "month";
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final Set<String> BILLING_CYCLES = Set.of(
            "week", "two_weeks", "month", "quarter", "half_year", "year"
    );

    private final IMembershipMapper membershipMapper;
    private final IMembershipService membershipService;

    @GetMapping("/list")
    public ApiResponse<List<MembershipVO>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MembershipRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MembershipRecordEntity::getUserId, userId)
                .orderByAsc(MembershipRecordEntity::getExpiryDate);
        LocalDate today = LocalDate.now();
        List<MembershipVO> voList = membershipService.list(queryWrapper).stream()
                .map(entity -> toVO(entity, today))
                .toList();
        return ApiResponse.success(voList);
    }

    @GetMapping("/stats")
    public ApiResponse<MembershipStatsVO> stats() {
        long userId = StpUtil.getLoginIdAsLong();
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LambdaQueryWrapper<MembershipRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MembershipRecordEntity::getUserId, userId);
        List<MembershipRecordEntity> entities = membershipService.list(queryWrapper);

        MembershipStatsVO vo = new MembershipStatsVO();
        vo.setActiveCount(entities.stream().filter(e -> STATUS_ACTIVE.equals(calcStatus(e, today))).count());
        vo.setExpiringCount(entities.stream().filter(e -> STATUS_EXPIRING.equals(calcStatus(e, today))).count());
        vo.setExpiredCount(entities.stream().filter(e -> STATUS_EXPIRED.equals(calcStatus(e, today))).count());
        vo.setExpiringThisMonthCount(entities.stream()
                .filter(e -> !e.getExpiryDate().isBefore(today) && !e.getExpiryDate().isAfter(monthEnd))
                .count());
        vo.setMonthlyAmount(entities.stream()
                .filter(e -> !e.getExpiryDate().isBefore(today))
                .filter(e -> e.getStartDate() == null || !e.getStartDate().isAfter(today))
                .map(MembershipRecordEntity::getMonthlyAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return ApiResponse.success(vo);
    }

    @GetMapping("/{id}")
    public ApiResponse<MembershipVO> detail(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MembershipRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MembershipRecordEntity::getId, id)
                .eq(MembershipRecordEntity::getUserId, userId);
        MembershipRecordEntity entity = membershipService.getOne(queryWrapper);
        return ApiResponse.success(toVO(entity, LocalDate.now()));
    }

    @PostMapping
    public ApiResponse<MembershipVO> create(@RequestBody MembershipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        normalizeCost(req);
        MembershipRecordEntity entity = new MembershipRecordEntity();
        BeanUtil.copyProperties(req, entity);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        membershipMapper.insert(entity);
        return ApiResponse.success(toVO(entity, LocalDate.now()));
    }

    @PutMapping
    public ApiResponse<MembershipVO> update(@RequestBody MembershipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        normalizeCost(req);
        MembershipRecordEntity entity = new MembershipRecordEntity();
        BeanUtil.copyProperties(req, entity);
        entity.setUserId(null);
        entity.fillUpdateCommonField(userId);
        LambdaUpdateWrapper<MembershipRecordEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MembershipRecordEntity::getId, req.getId())
                .eq(MembershipRecordEntity::getUserId, userId);
        membershipService.update(entity, updateWrapper);
        MembershipRecordEntity updated = membershipService.getOne(new LambdaQueryWrapper<MembershipRecordEntity>()
                .eq(MembershipRecordEntity::getId, req.getId())
                .eq(MembershipRecordEntity::getUserId, userId));
        return ApiResponse.success(toVO(updated, LocalDate.now()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<MembershipRecordEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MembershipRecordEntity::getId, id)
                .eq(MembershipRecordEntity::getUserId, userId)
                .set(MembershipRecordEntity::getIsDeleted, 1)
                .set(MembershipRecordEntity::getUpdateUser, userId)
                .set(MembershipRecordEntity::getUpdateTime, LocalDateTime.now());
        membershipService.update(null, updateWrapper);
        return ApiResponse.success();
    }

    private MembershipVO toVO(MembershipRecordEntity entity, LocalDate today) {
        if (entity == null) {
            return null;
        }
        MembershipVO vo = new MembershipVO();
        BeanUtil.copyProperties(entity, vo);
        vo.setStatus(calcStatus(entity, today));
        vo.setRemainingDays(ChronoUnit.DAYS.between(today, entity.getExpiryDate()));
        return vo;
    }

    private String calcStatus(MembershipRecordEntity entity, LocalDate today) {
        if (entity.getExpiryDate().isBefore(today)) {
            return STATUS_EXPIRED;
        }
        if (!entity.getExpiryDate().isAfter(today.plusDays(DEFAULT_REMIND_DAYS))) {
            return STATUS_EXPIRING;
        }
        return STATUS_ACTIVE;
    }

    private void normalizeCost(MembershipReq req) {
        String billingCycle = req.getBillingCycle();
        if (billingCycle == null || billingCycle.isBlank()) {
            billingCycle = DEFAULT_BILLING_CYCLE;
            req.setBillingCycle(billingCycle);
        }
        if (!BILLING_CYCLES.contains(billingCycle)) {
            throw new IllegalArgumentException("不支持的计费周期");
        }
        if (req.getPrice() != null && req.getPrice().signum() < 0) {
            throw new IllegalArgumentException("支付金额不能小于0");
        }
        if (req.getMonthlyAmount() != null && req.getMonthlyAmount().signum() < 0) {
            throw new IllegalArgumentException("月均金额不能小于0");
        }
        if (req.getMonthlyAmount() == null) {
            req.setMonthlyAmount(calculateMonthlyAmount(req.getPrice(), billingCycle));
        } else {
            req.setMonthlyAmount(req.getMonthlyAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal calculateMonthlyAmount(BigDecimal price, String billingCycle) {
        if (price == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
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
}
