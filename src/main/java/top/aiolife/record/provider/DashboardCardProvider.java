package top.aiolife.record.provider;

import top.aiolife.record.pojo.vo.DashboardCardVO;

/**
 * 看板卡片提供者接口
 *
 * @author Lys
 * @date 2026/01/23 23:08
 */
public interface DashboardCardProvider {
    /**
     * 获取卡片类型唯一标识
     */
    String getType();

    /**
     * 卡片标题
     */
    String getTitle();

    /**
     * 总量标题
     */
    String getTotalTitle();

    /**
     * 图标
     */
    String getIcon();

    /**
     * 图标颜色，默认使用前端主题颜色
     */
    default String getIconColor() {
        return null;
    }

    /**
     * 获取卡片数据
     */
    DashboardCardVO getCard(long userId);

    /**
     * 排序权重
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 是否可见
     */
    default boolean isVisible(long userId) {
        return true;
    }
}
