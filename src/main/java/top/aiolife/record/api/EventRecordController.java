package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.EventRecordEntity;
import top.aiolife.record.pojo.req.EventRecordCreateReq;
import top.aiolife.record.pojo.req.EventRecordUpdateReq;
import top.aiolife.record.pojo.vo.EventRecordVO;
import top.aiolife.record.service.EventRecordService;

/**
 * 活动记录控制器
 *
 * @author Lys
 * @date 2025/04/07 22:31
 */
@Tag(name = "活动记录")
@RestController
@RequiredArgsConstructor
@RequestMapping("/event-records")
public class EventRecordController {

    private final EventRecordService eventRecords;

    /**
     * 分页查询活动记录
     */
    @Operation(summary = "分页查询活动记录")
    @GetMapping
    public ApiResponse<PageResp<EventRecordVO>> queryEventRecords(@CurrentUserId String userId,
                                                                  @RequestParam(defaultValue = "1") long page,
                                                                  @RequestParam(defaultValue = "10") long pageSize) {
        PageResp<EventRecordEntity> objectPageResp = eventRecords.list(
                IdCodec.decode(userId), page, pageSize);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toEventRecordVOPage(objectPageResp));
    }

    /**
     * 新增活动记录
     */
    @Operation(summary = "新增活动记录")
    @PostMapping
    public ApiResponse<EventRecordVO> createEventRecord(@CurrentUserId String userId,
                                                        @Valid @RequestBody EventRecordCreateReq entityReq) {
        EventRecordEntity entity = RecordApiConvertor.INSTANCE.fromEventRecordCreateReq(entityReq);
        entity = eventRecords.create(entity, IdCodec.decode(userId));
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toEventRecordVO(entity));
    }

    /**
     * 更新活动记录
     */
    @Operation(summary = "更新活动记录")
    @PutMapping
    public ApiResponse<EventRecordVO> updateEventRecord(@CurrentUserId String userId,
                                                        @Valid @RequestBody EventRecordUpdateReq entityReq) {
        EventRecordEntity entity = RecordApiConvertor.INSTANCE.fromEventRecordUpdateReq(entityReq);
        entity = eventRecords.update(entity, IdCodec.decode(userId));
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toEventRecordVO(entity));
    }

    /**
     * 删除活动记录
     */
    @Operation(summary = "删除活动记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteEventRecord(@CurrentUserId String userId,
                                                  @PathVariable String id) {
        boolean b = eventRecords.delete(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success(b);
    }
}
