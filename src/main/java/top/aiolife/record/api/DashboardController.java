package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.service.DashboardService;

/**
 * 看板
 *
 * @author Lys
 * @date 2025/04/13 13:56
 */
@Tag(name = "看板")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 查询可见首页卡片配置，不含实时业务数据。
     */
    @Operation(summary = "查询首页卡片配置")
    @GetMapping("/tasks")
    public ApiResponse<List<DashboardCardVO>> getTasks(@CurrentUserId String userId) {
        return ApiResponse.success(dashboardService.listCards(IdCodec.decode(userId)));
    }

    /**
     * 查询指定类型的首页卡片数据。
     */
    @Operation(summary = "查询指定首页卡片数据")
    @GetMapping("/card/{type}")
    public ApiResponse<DashboardCardVO> getCardDetail(@CurrentUserId String userId,
                                                      @PathVariable String type) {
        return ApiResponse.success(dashboardService.getCard(IdCodec.decode(userId), type));
    }
}
