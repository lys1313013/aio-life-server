package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.query.WereadStatsQuery;
import top.aiolife.record.pojo.vo.HomeSectionVO;
import top.aiolife.record.pojo.vo.WereadStatsVO;
import top.aiolife.record.service.HomeSectionService;
import top.aiolife.record.service.WereadStatsService;

/** 每张卡独立请求，错误及二级锁只影响当前业务区域。 */
@Tag(name = "首页卡片数据")
@RestController
@RequestMapping("/home/sections")
@RequiredArgsConstructor
public class HomeSectionController {
    private final HomeSectionService service;
    private final WereadStatsService wereadStatsService;

    @Operation(summary = "查询首页备忘摘要")
    @GetMapping("/memo")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Memo>> memos(@CurrentUserId String userId) {
        return ApiResponse.success(service.memos(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询首页视频摘要")
    @GetMapping("/video")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Video>> videos(@CurrentUserId String userId) {
        return ApiResponse.success(service.videos(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询首页设备摘要")
    @GetMapping("/device")
    public ApiResponse<HomeSectionVO.Items<HomeSectionVO.Device>> devices(@CurrentUserId String userId) {
        return ApiResponse.success(service.devices(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询首页微信读书统计")
    @GetMapping("/weread-stats")
    public ApiResponse<WereadStatsVO> wereadStats(@CurrentUserId String userId,
                                                  @Valid @QueryParams WereadStatsQuery query) {
        return ApiResponse.success(
                wereadStatsService.stats(IdCodec.decode(userId), query.getGranularity()));
    }
}
