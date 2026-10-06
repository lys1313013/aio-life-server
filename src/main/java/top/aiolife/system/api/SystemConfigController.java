package top.aiolife.system.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
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
@RestController
@RequestMapping("/system-config")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class SystemConfigController {

    private final ISystemConfigService systemConfigService;

    /**
     * 配置列表（按 key 前缀筛选）
     */
    @GetMapping("/list")
    public ApiResponse<List<SystemConfigVO>> list(@RequestParam(required = false) String keyPrefix) {
        return ApiResponse.success(systemConfigService.list(keyPrefix));
    }

    /**
     * 获取单项配置
     */
    @GetMapping("/{key}")
    public ApiResponse<SystemConfigVO> getByKey(@PathVariable String key) {
        SystemConfigVO vo = systemConfigService.getByKey(key);
        if (vo == null) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "配置项不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 更新单项配置
     */
    @PutMapping("/{key}")
    public ApiResponse<SystemConfigVO> update(@PathVariable String key,
                                              @RequestBody SystemConfigUpdateReq req) {
        long operatorId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(systemConfigService.update(key, req, operatorId));
    }
}
