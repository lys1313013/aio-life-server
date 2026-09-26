package top.aiolife.system.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.system.pojo.req.UserMenuHiddenSaveReq;
import top.aiolife.system.pojo.vo.UserMenuPreferenceVO;
import top.aiolife.system.service.UserMenuPreferenceService;

/** 当前用户个人菜单显示设置；用户身份只能来自登录态。 */
@RestController
@RequestMapping("/menu/preferences")
@RequiredArgsConstructor
public class UserMenuPreferenceController {
    private final UserMenuPreferenceService service;

    @GetMapping
    public ApiResponse<UserMenuPreferenceVO> get() {
        return ApiResponse.success(service.get(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping
    public ApiResponse<UserMenuPreferenceVO> save(@RequestBody UserMenuHiddenSaveReq req) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(), req.getMenuIds()));
    }

    @DeleteMapping
    public ApiResponse<UserMenuPreferenceVO> reset() {
        return ApiResponse.success(service.reset(StpUtil.getLoginIdAsLong()));
    }
}
