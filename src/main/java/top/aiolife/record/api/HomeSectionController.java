package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.vo.HomeSectionVO;
import top.aiolife.record.service.HomeSectionService;

/** 每张卡独立请求，错误及二级锁只影响当前业务区域。 */
@RestController
@RequestMapping("/home/sections")
@RequiredArgsConstructor
public class HomeSectionController {
    private final HomeSectionService service;

    @GetMapping("/memo")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Memo>> memos() {
        return ApiResponse.success(service.memos(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/video")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Video>> videos() {
        return ApiResponse.success(service.videos(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/device")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Device>> devices() {
        return ApiResponse.success(service.devices(StpUtil.getLoginIdAsLong()));
    }
}
