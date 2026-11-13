package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.system.pojo.req.SystemConfigUpdateReq;
import top.aiolife.system.pojo.vo.SystemConfigVO;
import top.aiolife.system.service.ISystemConfigService;

import java.util.List;

/**
 * 系统配置控制器（管理员）
 *
 * <p>通用 KV 配置管理。典型用途：维护反馈通知接收人列表、功能开关等。</p>
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/system-config")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class SystemConfigController {

    private final ISystemConfigService systemConfigService;

    @Operation(summary = "查询系统配置列表")
    @GetMapping("/list")
    public ApiResponse<List<SystemConfigVO>> list(@RequestParam(required = false) String keyPrefix) {
        return ApiResponse.success(systemConfigService.list(keyPrefix));
    }

    @Operation(summary = "查询指定系统配置")
    @GetMapping("/{key}")
    public ApiResponse<SystemConfigVO> getByKey(@PathVariable String key) {
        return ApiResponse.success(systemConfigService.getRequiredByKey(key));
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/{key}")
    public ApiResponse<SystemConfigVO> update(@CurrentUserId String userId, @PathVariable String key,
                                              @Valid @RequestBody SystemConfigUpdateReq req) {
        return ApiResponse.success(systemConfigService.update(key, req, IdCodec.decode(userId)));
    }
}
