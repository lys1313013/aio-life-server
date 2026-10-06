package top.aiolife.wardrobe.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 衣柜统计数据 VO
 */
@Data
public class WardrobeStatsVO {

    /**
     * 衣物总数量
     */
    private Integer totalCount;

    /**
     * 各分类数量
     */
    private Map<String, Integer> categoryCount;

    /**
     * 季节分布
     */
    private Map<String, Integer> seasonCount;

    /**
     * 总价值
     */
    private BigDecimal totalValue;

    /**
     * 平均价格
     */
    private BigDecimal avgPrice;
}
