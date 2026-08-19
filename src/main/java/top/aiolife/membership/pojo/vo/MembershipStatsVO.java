package top.aiolife.membership.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员统计视图对象
 *
 * @author Lys
 * @date 2026/08/06
 */
@Data
public class MembershipStatsVO {

    private Long activeCount;

    private Long expiringCount;

    private Long expiredCount;

    private Long expiringThisMonthCount;

    /**
     * 当前未过期会员的月均成本合计
     */
    private BigDecimal monthlyAmount;
}
