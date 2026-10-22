package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
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
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.req.ExpenseDeviceBindReq;
import top.aiolife.record.pojo.vo.DeviceVO;
import top.aiolife.record.pojo.vo.ExpenseDeviceContextVO;
import top.aiolife.record.service.DeviceExpenseService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expense/{expenseId}/device")
public class ExpenseDeviceController {
    private final DeviceExpenseService service;

    @GetMapping
    public ApiResponse<ExpenseDeviceContextVO> context(@PathVariable long expenseId) {
        return ApiResponse.success(service.context(StpUtil.getLoginIdAsLong(), expenseId));
    }

    @PutMapping
    public ApiResponse<DeviceVO> bind(@PathVariable long expenseId, @Valid @RequestBody ExpenseDeviceBindReq request) {
        return ApiResponse.success(service.bind(StpUtil.getLoginIdAsLong(), expenseId, request.getDeviceId()));
    }

    @PostMapping
    public ApiResponse<DeviceVO> create(@PathVariable long expenseId, @Valid @RequestBody DeviceCreateReq request) {
        return ApiResponse.success(service.createAndBind(StpUtil.getLoginIdAsLong(), expenseId, request));
    }

    @DeleteMapping
    public ApiResponse<Boolean> unbind(@PathVariable long expenseId) {
        service.unbind(StpUtil.getLoginIdAsLong(), expenseId);
        return ApiResponse.success(true);
    }
}
