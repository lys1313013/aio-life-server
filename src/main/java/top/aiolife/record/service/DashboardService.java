package top.aiolife.record.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.system.service.MenuClient;
import top.aiolife.system.service.MenuVisualService;

/**
 * 看板卡片装配服务。
 *
 * @author Lys
 * @date 2026/01/23 23:08
 */
@Service
public class DashboardService {

    private final Map<String, DashboardCardProvider> providerMap;
    private final MenuVisualService visuals;

    public DashboardService(List<DashboardCardProvider> providers, MenuVisualService visuals) {
        this.visuals = visuals;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(DashboardCardProvider::getType, p -> p));
    }

    /**
     * 查询可见首页卡片配置，不含实时业务数据。
     */
    public List<DashboardCardVO> listCards(long userId) {
        var cardVisuals = visuals.get(userId, MenuClient.WEB).cards();
        return providerMap.values().stream()
                .filter(provider -> provider.isVisible(userId))
                .sorted(Comparator.comparingInt(DashboardCardProvider::getOrder))
                .map(provider -> {
                    DashboardCardVO card = new DashboardCardVO();
                    card.setType(provider.getType());
                    card.setTitle(provider.getTitle());
                    card.setTotalTitle(provider.getTotalTitle());
                    var visual = cardVisuals.getOrDefault(
                            "overview." + provider.getType().toLowerCase(Locale.ROOT),
                            MenuVisualService.DEFAULT);
                    card.setIcon(visual.icon());
                    card.setIconColor(visual.iconColor());
                    return card;
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询指定类型的首页卡片数据。
     */
    public DashboardCardVO getCard(long userId, String type) {
        DashboardCardProvider provider = providerMap.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("未知任务类型");
        }
        DashboardCardVO card = provider.getCard(userId);
        if (card != null) {
            var visual = visuals.get(userId, MenuClient.WEB).cards().getOrDefault(
                    "overview." + type.toLowerCase(Locale.ROOT), MenuVisualService.DEFAULT);
            card.setIcon(visual.icon());
            card.setIconColor(visual.iconColor());
        }
        return card;
    }
}
