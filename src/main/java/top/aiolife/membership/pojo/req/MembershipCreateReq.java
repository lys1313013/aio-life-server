package top.aiolife.membership.pojo.req;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * 会员请求对象
 *
 * @author Lys
 * @date 2026/08/06
 */
@Data
public class MembershipCreateReq {


    private String name;

    private String category;

    private String provider;

    private String icon;

    private String color;

    private LocalDate startDate;

    private LocalDate expiryDate;

    private BigDecimal price;

    private String billingCycle;

    private BigDecimal monthlyAmount;

    private Integer autoRenew;

    private String note;
}
