package top.aiolife.wardrobe.pojo.dto;

import java.math.BigDecimal;

/** 数据库衣物汇总；数量在响应边界检查后转换为 Integer。 */
public record WardrobeStatsAggregate(long totalCount, BigDecimal totalValue,
                                     long springCount, long summerCount,
                                     long autumnCount, long winterCount) {
}
