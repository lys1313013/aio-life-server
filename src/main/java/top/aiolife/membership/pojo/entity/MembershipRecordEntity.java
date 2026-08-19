package top.aiolife.membership.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.aiolife.record.pojo.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 会员记录实体
 *
 * @author Lys
 * @date 2026/08/06
 */
@Data
@TableName("membership_record")
public class MembershipRecordEntity extends BaseEntity {

    private Long userId;

    private String name;

    /**
     * video/music/shopping/cloud/study/game/other
     */
    private String category;

    private String provider;

    private String icon;

    private String color;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private BigDecimal price;

    /**
     * week/two_weeks/month/quarter/half_year/year
     */
    private String billingCycle;

    private BigDecimal monthlyAmount;

    private Integer autoRenew;

    private String note;
}
