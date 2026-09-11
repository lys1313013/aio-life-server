package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public DashboardController(List<DashboardCardProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(DashboardCardProvider::getType, p -> p));
    }

    /**
     * 1. 获取任务列表
     * 前端拿到后，可以立即渲染占位图（Skeleton Screen）
     * 只返回静态配置信息（标题、图标等），不查询实时数据
     */
    @GetMapping("/tasks")
    public ApiResponse<List<DashboardCardVO>> getTasks() {
        long userId = StpUtil.getLoginIdAsLong();
        List<DashboardCardVO> tasks = providerMap.values().stream()
                .filter(provider -> provider.isVisible(userId))
                .sorted(Comparator.comparingInt(DashboardCardProvider::getOrder))
                .map(provider -> {
                    DashboardCardVO card = new DashboardCardVO();
                    card.setType(provider.getType());
                    card.setTitle(provider.getTitle());
                    card.setTotalTitle(provider.getTotalTitle());
                    card.setIcon(provider.getIcon());
                    card.setIconColor(provider.getIconColor());
                    return card;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(tasks);
    }

    /**
     * 2. 根据类型获取单个卡片详情
     * 前端并发调用：/card/EXERCISE, /card/LEETCODE ...
     */
    @GetMapping("/card/{type}")
    public ApiResponse<DashboardCardVO> getCardDetail(@PathVariable String type) {
        DashboardCardProvider provider = providerMap.get(type);
        if (provider == null) {
            return ApiResponse.error("未知任务类型");
        }
        long userId = StpUtil.getLoginIdAsLong();
        DashboardCardVO card = provider.getCard(userId);
        if (card != null && card.getIconColor() == null) {
            card.setIconColor(provider.getIconColor());
        }
        return ApiResponse.success(card);
    }
}
