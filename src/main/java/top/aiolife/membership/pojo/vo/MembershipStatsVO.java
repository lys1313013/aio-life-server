package top.aiolife.membership.pojo.vo;

import lombok.Data;

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
}
