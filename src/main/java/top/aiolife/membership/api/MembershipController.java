package top.aiolife.membership.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.membership.convertor.MembershipApiConvertor;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.entity.MembershipProviderEntity;
import top.aiolife.membership.pojo.req.MembershipCreateReq;
import top.aiolife.membership.pojo.req.MembershipReq;
import top.aiolife.membership.pojo.vo.MembershipStatsVO;
import top.aiolife.membership.pojo.vo.MembershipVO;
import top.aiolife.membership.service.MembershipProviderService;
import top.aiolife.membership.service.MembershipRecordWriteService;

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

    private final MembershipRecordWriteService recordWriter;
    private final MembershipProviderService providers;

    @GetMapping("/list")
    public ApiResponse<List<MembershipVO>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        LocalDate today = LocalDate.now();
        var entities = recordWriter.list(userId);
        Map<Long, MembershipProviderEntity> platformMap = providers.findAll(entities.stream().map(MembershipRecordEntity::getProviderId).toList());
        List<MembershipVO> voList = entities.stream()
                .map(entity -> toVO(entity, today, entity.getProviderId() == null ? null : platformMap.get(entity.getProviderId())))
                .toList();
        return ApiResponse.success(voList);
    }

    @GetMapping("/stats")
    public ApiResponse<MembershipStatsVO> stats() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(recordWriter.stats(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<MembershipVO> detail(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        var entity = recordWriter.getOwned(userId, id);
        return ApiResponse.success(toVO(entity, LocalDate.now()));
    }

    @PostMapping
    public ApiResponse<MembershipVO> create(@RequestBody MembershipCreateReq request) {
        MembershipReq req = MembershipApiConvertor.INSTANCE.fromMembershipCreateReq(request);
        long userId = StpUtil.getLoginIdAsLong();
        recordWriter.normalizeCost(req);
        MembershipRecordEntity entity = recordWriter.create(userId, req);
        return ApiResponse.success(toVO(entity, LocalDate.now()));
    }

    @PutMapping
    public ApiResponse<MembershipVO> update(@RequestBody MembershipReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        recordWriter.normalizeCost(req);
        MembershipRecordEntity updated = recordWriter.update(userId, req);
        return ApiResponse.success(toVO(updated, LocalDate.now()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        recordWriter.delete(userId, id);
        return ApiResponse.success();
    }

    private MembershipVO toVO(MembershipRecordEntity entity, LocalDate today) {
        MembershipProviderEntity provider = entity == null || entity.getProviderId() == null ? null
                : providers.findAll(List.of(entity.getProviderId())).get(entity.getProviderId());
        return toVO(entity, today, provider);
    }

    private MembershipVO toVO(MembershipRecordEntity entity, LocalDate today, MembershipProviderEntity provider) {
        if (entity == null) {
            return null;
        }
        MembershipVO vo = new MembershipVO();
        BeanUtil.copyProperties(entity, vo);
        if (provider != null) {
            vo.setProviderName(provider.getName());
            vo.setProviderIconKey(provider.getIconKey());
        }
        vo.setStatus(recordWriter.status(entity, today));
        vo.setRemainingDays(ChronoUnit.DAYS.between(today, entity.getExpiryDate()));
        return vo;
    }

}
