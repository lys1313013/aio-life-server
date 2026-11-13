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
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.req.ExpenseDeviceBindReq;
import top.aiolife.record.pojo.vo.DeviceVO;
import top.aiolife.record.pojo.vo.ExpenseDeviceContextVO;
import top.aiolife.record.service.DeviceExpenseService;

/**
 * 支出设备关联控制器
 *
 * @author Lys
 */
@Tag(name = "支出设备关联")
@RestController
@RequiredArgsConstructor
@RequestMapping("/expense/{expenseId}/device")
public class ExpenseDeviceController {

    private final DeviceExpenseService service;

    @Operation(summary = "查询支出关联设备及候选项")
    @GetMapping
    public ApiResponse<ExpenseDeviceContextVO> context(@CurrentUserId String userId,
                                                       @PathVariable String expenseId) {
        return ApiResponse.success(service.context(
                IdCodec.decode(userId), IdCodec.decode(expenseId)));
    }

    @Operation(summary = "为支出关联已有设备")
    @PutMapping
    public ApiResponse<DeviceVO> bind(@CurrentUserId String userId, @PathVariable String expenseId,
                                      @Valid @RequestBody ExpenseDeviceBindReq request) {
        return ApiResponse.success(service.bind(
                IdCodec.decode(userId), IdCodec.decode(expenseId), request.getDeviceId()));
    }

    @Operation(summary = "新建设备并关联支出")
    @PostMapping
    public ApiResponse<DeviceVO> create(@CurrentUserId String userId, @PathVariable String expenseId,
                                        @Valid @RequestBody DeviceCreateReq request) {
        return ApiResponse.success(service.createAndBind(
                IdCodec.decode(userId), IdCodec.decode(expenseId), request));
    }

    @Operation(summary = "解除支出与设备的关联")
    @DeleteMapping
    public ApiResponse<Boolean> unbind(@CurrentUserId String userId, @PathVariable String expenseId) {
        service.unbind(IdCodec.decode(userId), IdCodec.decode(expenseId));
        return ApiResponse.success(true);
    }
}
