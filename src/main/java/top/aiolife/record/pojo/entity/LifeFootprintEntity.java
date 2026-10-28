package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 长期生活地点；起止日期精确到日。 */
@Getter
@Setter
@TableName("life_footprint")
public class LifeFootprintEntity extends BaseEntity {
    private Long userId;
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private String placeName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String regionCode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coordinateSystem;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String providerUid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
