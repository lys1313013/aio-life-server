package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.MilestoneEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.MilestoneCreateReq;
import top.aiolife.record.pojo.req.MilestoneUpdateReq;
import top.aiolife.record.pojo.vo.MilestoneVO;
import top.aiolife.record.service.IMilestoneService;

/**
 * 里程碑控制器
 *
 * @author Lys
 * @date 2026-02-07 16:33
 */
@Tag(name = "里程碑")
@RestController
@AllArgsConstructor
@RequestMapping("/milestones")
public class MilestoneController {

    private final IMilestoneService milestoneService;

    @Operation(summary = "查询里程碑")
    @GetMapping
    public ApiResponse<List<MilestoneVO>> queryMilestone(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVOList(
                milestoneService.listForUser(IdCodec.decode(userId))));
    }

    @Operation(summary = "新增里程碑")
    @PostMapping
    public ApiResponse<MilestoneVO> createMilestone(@CurrentUserId String userId,
                                                    @Valid @RequestBody MilestoneCreateReq request) {
        MilestoneEntity entity = RecordApiConvertor.INSTANCE.fromMilestoneCreateReq(request);
        entity = milestoneService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVO(entity));
    }

    @Operation(summary = "更新里程碑")
    @PutMapping
    public ApiResponse<MilestoneVO> updateMilestone(@CurrentUserId String userId,
                                                    @Valid @RequestBody MilestoneUpdateReq request) {
        MilestoneEntity entity = RecordApiConvertor.INSTANCE.fromMilestoneUpdateReq(request);
        entity = milestoneService.updateForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMilestoneVO(entity));
    }

    @Operation(summary = "批量删除里程碑")
    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteMilestone(@CurrentUserId String userId,
                                             @Valid @RequestBody CommonReq commonReq) {
        milestoneService.deleteForUser(IdCodec.decode(userId), commonReq.getIdList());
        return ApiResponse.success();
    }
}
