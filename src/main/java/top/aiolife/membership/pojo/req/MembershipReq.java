package top.aiolife.membership.pojo.req;

import lombok.Data;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 会员请求对象
 *
 * @author Lys
 * @date 2026/08/06
 */
@Data
public class MembershipReq {

    private Long id;

    private String name;

    private String category;

    private String provider;

    private Long providerId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean providerIdProvided;

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
        this.providerIdProvided = true;
    }

    public boolean hasProviderId() { return providerIdProvided; }

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
