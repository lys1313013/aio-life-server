package top.aiolife.membership.pojo.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 数据库统计结果；数量在响应边界检查后转换为 Integer。 */
@Data
public class MembershipStatsAggregate {
    private long activeCount;
    private long expiringCount;
    private long expiredCount;
    private long expiringThisMonthCount;
    private BigDecimal monthlyAmount;
}
