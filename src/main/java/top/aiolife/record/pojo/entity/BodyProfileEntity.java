package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 每个用户仅保存一份当前身体档案，不保留历史快照。 */
@Getter
@Setter
@TableName("user_body_profile")
public class BodyProfileEntity extends BaseEntity {
    private Long userId;
    private Integer version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal heightCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal weightKg;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal bodyFatPercent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal shoulderWidthCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal chestCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal upperArmCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal forearmCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal waistCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal hipCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal thighCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal calfCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal inseamCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String gender;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String measurementSide;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String armState;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shoeSystem;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String topSize;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String trousersSize;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shoeSize;
}
