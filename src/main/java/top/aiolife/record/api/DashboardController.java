package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.system.service.MenuVisualService;
import top.aiolife.system.service.MenuClient;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板
 *
 * @author Lys
 * @date 2025/04/13 13:56
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final Map<String, DashboardCardProvider> providerMap;
    private final MenuVisualService visuals;

    public DashboardController(List<DashboardCardProvider> providers, MenuVisualService visuals) {
        this.visuals = visuals;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(DashboardCardProvider::getType, p -> p));
    }

    /**
     * 查询可见首页卡片配置，不含实时业务数据。
     */
    @Operation(summary = "查询首页卡片配置")
    @GetMapping("/tasks")
    public ApiResponse<List<DashboardCardVO>> getTasks() {
        long userId = StpUtil.getLoginIdAsLong();
        var cardVisuals = visuals.get(userId, MenuClient.WEB).cards();
        List<DashboardCardVO> tasks = providerMap.values().stream()
                .filter(provider -> provider.isVisible(userId))
                .sorted(Comparator.comparingInt(DashboardCardProvider::getOrder))
                .map(provider -> {
                    DashboardCardVO card = new DashboardCardVO();
                    card.setType(provider.getType());
                    card.setTitle(provider.getTitle());
                    card.setTotalTitle(provider.getTotalTitle());
                    var visual = cardVisuals.getOrDefault("overview." + provider.getType().toLowerCase(Locale.ROOT), MenuVisualService.DEFAULT);
                    card.setIcon(visual.icon());
                    card.setIconColor(visual.iconColor());
                    return card;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(tasks);
    }

    /**
     * 查询指定类型的首页卡片数据。
     */
    @Operation(summary = "查询指定首页卡片数据")
    @GetMapping("/card/{type}")
    public ApiResponse<DashboardCardVO> getCardDetail(@PathVariable String type) {
        DashboardCardProvider provider = providerMap.get(type);
        if (provider == null) {
            return ApiResponse.error("未知任务类型");
        }
        long userId = StpUtil.getLoginIdAsLong();
        DashboardCardVO card = provider.getCard(userId);
        if (card != null) {
            var visual = visuals.get(userId, MenuClient.WEB).cards().getOrDefault(
                "overview." + type.toLowerCase(Locale.ROOT), MenuVisualService.DEFAULT);
            card.setIcon(visual.icon());
            card.setIconColor(visual.iconColor());
        }
        return ApiResponse.success(card);
    }
}
