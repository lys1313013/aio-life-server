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
import top.aiolife.record.pojo.entity.HonorRecordEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.HonorRecordCreateReq;
import top.aiolife.record.pojo.req.HonorRecordUpdateReq;
import top.aiolife.record.pojo.vo.HonorRecordVO;
import top.aiolife.record.service.IHonorRecordService;

/**
 * 荣誉记录控制器
 *
 * @author Lys
 * @date 2026/04/11
 */
@Tag(name = "荣誉")
@RestController
@AllArgsConstructor
@RequestMapping("/honorRecords")
public class HonorRecordController {

    private final IHonorRecordService honorRecordService;

    @Operation(summary = "查询荣誉记录")
    @GetMapping
    public ApiResponse<List<HonorRecordVO>> queryHonorRecords(@CurrentUserId String userId) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorRecordVOList(
                honorRecordService.listForUser(IdCodec.decode(userId))));
    }

    @Operation(summary = "查询荣誉记录详情")
    @GetMapping("/{id}")
    public ApiResponse<HonorRecordVO> getHonorRecord(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorRecordVO(
                honorRecordService.getOwned(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    @Operation(summary = "新增荣誉记录")
    @PostMapping
    public ApiResponse<HonorRecordVO> createHonorRecord(@CurrentUserId String userId,
                                                        @Valid @RequestBody HonorRecordCreateReq request) {
        HonorRecordEntity entity = RecordApiConvertor.INSTANCE.fromHonorRecordCreateReq(request);
        entity = honorRecordService.createForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorRecordVO(entity));
    }

    @Operation(summary = "更新荣誉记录")
    @PutMapping
    public ApiResponse<HonorRecordVO> updateHonorRecord(@CurrentUserId String userId,
                                                        @Valid @RequestBody HonorRecordUpdateReq request) {
        HonorRecordEntity entity = RecordApiConvertor.INSTANCE.fromHonorRecordUpdateReq(request);
        entity = honorRecordService.updateForUser(IdCodec.decode(userId), entity);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toHonorRecordVO(entity));
    }

    @Operation(summary = "批量删除荣誉记录")
    @PostMapping("/batchDelete")
    public ApiResponse<Void> deleteHonorRecords(@CurrentUserId String userId,
                                                @Valid @RequestBody CommonReq commonReq) {
        honorRecordService.deleteForUser(IdCodec.decode(userId), commonReq.getIdList());
        return ApiResponse.success();
    }

    @Operation(summary = "切换荣誉置顶状态")
    @PostMapping("/toggleTop/{id}")
    public ApiResponse<Void> toggleTop(@CurrentUserId String userId, @PathVariable String id) {
        honorRecordService.toggleTop(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
