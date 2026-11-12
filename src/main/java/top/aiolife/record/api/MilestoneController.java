package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.core.query.QueryOrder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IMilestoneMapper;
import top.aiolife.record.pojo.entity.MilestoneEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.MilestoneCreateReq;
import top.aiolife.record.pojo.req.MilestoneUpdateReq;
import top.aiolife.record.pojo.vo.MilestoneVO;
import top.aiolife.record.service.IMilestoneService;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 16:33
 */
@RestController
@AllArgsConstructor
@RequestMapping("/milestones")
public class MilestoneController {
    private final IMilestoneMapper milestoneMapper;

    private final IMilestoneService milestoneService;

    public IMilestoneMapper getBaseMapper() {
        return milestoneMapper;
    }


    @Operation(summary = "查询里程碑")
    @GetMapping
    public ApiResponse<List<MilestoneVO>> queryMilestone() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<MilestoneEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MilestoneEntity::getUserId, userId);
        QueryOrder.descNullsLast(queryWrapper, MilestoneEntity::getCreateTime, MilestoneEntity::getId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVOList(milestoneService.list(queryWrapper)));
    }


    @Operation(summary = "新增里程碑")
    @PostMapping
    public ApiResponse<MilestoneVO> createMilestone(@Valid @RequestBody MilestoneCreateReq milestoneEntityReq) {
        MilestoneEntity milestoneEntity = RecordApiConvertor.INSTANCE.fromMilestoneCreateReq(milestoneEntityReq);
        long userId = StpUtil.getLoginIdAsLong();
        milestoneEntity.setUserId(userId);
        milestoneEntity.fillCreateCommonField(userId);
        milestoneMapper.insert(milestoneEntity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVO(milestoneEntity));
    }

    @Operation(summary = "更新里程碑")
    @PutMapping
    public ApiResponse<MilestoneVO> updateMilestone(@Valid @RequestBody MilestoneUpdateReq milestoneEntityReq) {
        MilestoneEntity milestoneEntity = RecordApiConvertor.INSTANCE.fromMilestoneUpdateReq(milestoneEntityReq);
        long userId = StpUtil.getLoginIdAsLong();
        milestoneEntity.fillUpdateCommonField(userId);
        milestoneEntity.setUserId(null);
        LambdaUpdateWrapper<MilestoneEntity> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(MilestoneEntity::getId, milestoneEntity.getId());
        lambdaUpdateWrapper.eq(MilestoneEntity::getUserId, userId);
        milestoneService.update(milestoneEntity, lambdaUpdateWrapper);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVO(milestoneEntity));
    }

    @Operation(summary = "批量删除里程碑")
    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteMilestone(@RequestBody CommonReq commonReq) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<MilestoneEntity> lambdaQueryWrapper = new LambdaUpdateWrapper<>();
        lambdaQueryWrapper.eq(MilestoneEntity::getUserId, userId);
        lambdaQueryWrapper.in(MilestoneEntity::getId, commonReq.getIdList());
        lambdaQueryWrapper.set(MilestoneEntity::getIsDeleted, 1);
        lambdaQueryWrapper.set(MilestoneEntity::getUpdateTime, LocalDateTime.now());
        lambdaQueryWrapper.set(MilestoneEntity::getUpdateUser, userId);
        milestoneService.update(null, lambdaQueryWrapper);
        return ApiResponse.success();
    }
}
