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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
}
