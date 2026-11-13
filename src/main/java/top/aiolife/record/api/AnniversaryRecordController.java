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
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;
import top.aiolife.record.pojo.req.AnniversaryRecordCreateReq;
import top.aiolife.record.pojo.req.AnniversaryRecordUpdateReq;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.HomePinReq;
import top.aiolife.record.pojo.req.HomePinnedOrderReq;
import top.aiolife.record.pojo.vo.AnniversaryRecordVO;
import top.aiolife.record.service.IAnniversaryRecordService;

/**
 * 纪念日记录控制器
 *
 * @author Lys
 * @date 2026/04/18
 */
@Tag(name = "纪念日")
@RestController
@AllArgsConstructor
@RequestMapping("/anniversaryRecords")
public class AnniversaryRecordController {

    private final IAnniversaryRecordService anniversaryRecordService;

    @Operation(summary = "查询纪念日列表")
    @GetMapping
    public ApiResponse<List<AnniversaryRecordVO>> queryAnniversaryRecords(
            @CurrentUserId String userId,
            @RequestParam(required = false) Integer isPinned) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVOList(
                anniversaryRecordService.listForUser(IdCodec.decode(userId), isPinned)));
    }

    @Operation(summary = "查询纪念日详情")
    @GetMapping("/{id}")
    public ApiResponse<AnniversaryRecordVO> getAnniversaryRecord(
            @CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(
                anniversaryRecordService.getOwned(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    @Operation(summary = "新增纪念日")
    @PostMapping
    public ApiResponse<AnniversaryRecordVO> createAnniversaryRecord(
            @CurrentUserId String userId, @Valid @RequestBody AnniversaryRecordCreateReq entityReq) {
        AnniversaryRecordEntity entity = RecordApiConvertor.INSTANCE.fromAnniversaryRecordCreateReq(entityReq);
        entity = anniversaryRecordService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @Operation(summary = "更新纪念日")
    @PutMapping
    public ApiResponse<AnniversaryRecordVO> updateAnniversaryRecord(
            @CurrentUserId String userId, @Valid @RequestBody AnniversaryRecordUpdateReq entityReq) {
        AnniversaryRecordEntity entity = RecordApiConvertor.INSTANCE.fromAnniversaryRecordUpdateReq(entityReq);
        entity = anniversaryRecordService.updateForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(entity));
    }

    @Operation(summary = "批量删除纪念日")
    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteAnniversaryRecords(
            @CurrentUserId String userId, @Valid @RequestBody CommonReq commonReq) {
        anniversaryRecordService.deleteForUser(IdCodec.decode(userId), commonReq.getIdList());
        return ApiResponse.success();
    }

    @Operation(summary = "设置纪念日置顶状态")
    @PutMapping("/{id}/pin")
    public ApiResponse<AnniversaryRecordVO> setPinned(
            @CurrentUserId String userId, @PathVariable String id,
            @Valid @RequestBody HomePinReq request) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toAnniversaryRecordVO(
                anniversaryRecordService.setPinned(
                        IdCodec.decode(userId), IdCodec.decode(id), request.getIsPinned())));
    }

    @Operation(summary = "调整置顶纪念日排序")
    @PutMapping("/pinned-order")
    public ApiResponse<Void> reorderPinned(
            @CurrentUserId String userId, @Valid @RequestBody HomePinnedOrderReq request) {
        anniversaryRecordService.reorderPinned(IdCodec.decode(userId), request.getIds());
        return ApiResponse.success();
    }
}
