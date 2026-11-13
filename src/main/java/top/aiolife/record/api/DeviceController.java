package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.query.DeviceQuery;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.req.DeviceUpdateReq;
import top.aiolife.record.pojo.vo.DeviceVO;
import top.aiolife.record.service.DeviceExpenseService;
import top.aiolife.record.service.IDeviceService;

/**
 * 设备控制器
 *
 * @author Lys
 * @date 2025/04/04 19:22
 */
@Tag(name = "设备")
@RestController
@AllArgsConstructor
@RequestMapping("/device")
public class DeviceController {

    private final IDeviceService deviceService;

    private final DeviceExpenseService deviceExpenseService;

    @Operation(summary = "分页查询设备")
    @GetMapping("/query")
    public ApiResponse<PageResp<DeviceVO>> query(@CurrentUserId String userId,
                                                 @QueryParams CommonQuery<DeviceQuery> requestQuery) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toDeviceVOPage(deviceService.pageForUser(
                IdCodec.decode(userId), requestQuery.getCondition(),
                requestQuery.getPage(), requestQuery.getPageSize())));
    }

    @Operation(summary = "新增设备")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody DeviceCreateReq entityReq) {
        DeviceEntity entity = RecordApiConvertor.INSTANCE.fromDeviceCreateReq(entityReq);
        return ApiResponse.success(deviceService.createForUser(IdCodec.decode(userId), entity));
    }

    @Operation(summary = "更新设备")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String id,
                                       @Valid @RequestBody DeviceUpdateReq entityReq) {
        DeviceEntity entity = RecordApiConvertor.INSTANCE.fromDeviceUpdateReq(entityReq);
        return ApiResponse.success(deviceService.updateForUser(
                IdCodec.decode(userId), IdCodec.decode(id), entity));
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(deviceExpenseService.deleteDevice(
                IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "查询设备详情")
    @GetMapping("/{id}")
    public ApiResponse<DeviceVO> getDevice(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toDeviceVO(
                deviceService.getOwnedOrNull(IdCodec.decode(userId), IdCodec.decode(id))));
    }
}
