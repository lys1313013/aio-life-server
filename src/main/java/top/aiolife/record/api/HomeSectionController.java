package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.query.WereadStatsQuery;
import top.aiolife.record.pojo.vo.HomeSectionVO;
import top.aiolife.record.pojo.vo.WereadStatsVO;
import top.aiolife.record.service.HomeSectionService;
import top.aiolife.record.service.WereadStatsService;

/** 每张卡独立请求，错误及二级锁只影响当前业务区域。 */
@RestController
@RequestMapping("/home/sections")
@RequiredArgsConstructor
public class HomeSectionController {
    private final HomeSectionService service;
    private final WereadStatsService wereadStatsService;

    @Operation(summary = "查询首页备忘摘要")
    @GetMapping("/memo")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Memo>> memos() {
        return ApiResponse.success(service.memos(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "查询首页视频摘要")
    @GetMapping("/video")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Video>> videos() {
        return ApiResponse.success(service.videos(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "查询首页设备摘要")
    @GetMapping("/device")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Device>> devices() {
        return ApiResponse.success(service.devices(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "查询首页微信读书统计")
    @GetMapping("/weread-stats")
    public ApiResponse<WereadStatsVO> wereadStats(@Valid @QueryParams WereadStatsQuery query) {
        return ApiResponse.success(
                wereadStatsService.stats(StpUtil.getLoginIdAsLong(), query.getGranularity()));
    }
}
