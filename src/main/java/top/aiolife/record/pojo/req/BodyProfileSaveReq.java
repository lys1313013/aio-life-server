package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 完整替换当前档案；可空业务字段传 null 表示清空。无用户、ID 或审计入参。 */
@Getter
@Setter
public class BodyProfileSaveReq {
    @NotNull(message = "请先读取当前档案")
    @Min(0)
    private Integer version;
    @DecimalMin(value = "0.1", message = "身高须大于0")
    @DecimalMax(value = "999.9", message = "身高超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "身高最多保留一位小数")
    private BigDecimal heightCm;
    @DecimalMin(value = "0.1", message = "体重须大于0")
    @DecimalMax(value = "999.9", message = "体重超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "体重最多保留一位小数")
    private BigDecimal weightKg;
    @DecimalMin(value = "0.1", message = "体脂率须大于0")
    @DecimalMax(value = "99.9", message = "体脂率超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "体脂率最多保留一位小数")
    private BigDecimal bodyFatPercent;
    @DecimalMin(value = "0.1", message = "肩宽须大于0")
    @DecimalMax(value = "999.9", message = "肩宽超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "肩宽最多保留一位小数")
    private BigDecimal shoulderWidthCm;
    @DecimalMin(value = "0.1", message = "胸围须大于0")
    @DecimalMax(value = "999.9", message = "胸围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "胸围最多保留一位小数")
    private BigDecimal chestCm;
    @DecimalMin(value = "0.1", message = "上臂围须大于0")
    @DecimalMax(value = "999.9", message = "上臂围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "上臂围最多保留一位小数")
    private BigDecimal upperArmCm;
    @DecimalMin(value = "0.1", message = "前臂围须大于0")
    @DecimalMax(value = "999.9", message = "前臂围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "前臂围最多保留一位小数")
    private BigDecimal forearmCm;
    @DecimalMin(value = "0.1", message = "腰围须大于0")
    @DecimalMax(value = "999.9", message = "腰围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "腰围最多保留一位小数")
    private BigDecimal waistCm;
    @DecimalMin(value = "0.1", message = "臀围须大于0")
    @DecimalMax(value = "999.9", message = "臀围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "臀围最多保留一位小数")
    private BigDecimal hipCm;
    @DecimalMin(value = "0.1", message = "大腿围须大于0")
    @DecimalMax(value = "999.9", message = "大腿围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "大腿围最多保留一位小数")
    private BigDecimal thighCm;
    @DecimalMin(value = "0.1", message = "小腿围须大于0")
    @DecimalMax(value = "999.9", message = "小腿围超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "小腿围最多保留一位小数")
    private BigDecimal calfCm;
    @DecimalMin(value = "0.1", message = "腿内侧长须大于0")
    @DecimalMax(value = "999.9", message = "腿内侧长超出可填写范围")
    @Digits(integer = 3, fraction = 1, message = "腿内侧长最多保留一位小数")
    private BigDecimal inseamCm;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    @Pattern(regexp = "male|female", message = "选项或鞋码格式不正确")
    private String gender;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    @Pattern(regexp = "left|right", message = "选项或鞋码格式不正确")
    private String measurementSide;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    @Pattern(regexp = "relaxed|flexed", message = "选项或鞋码格式不正确")
    private String armState;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    @Pattern(regexp = "EU|CN|US|UK", message = "选项或鞋码格式不正确")
    private String shoeSystem;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    private String topSize;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    private String trousersSize;
    @Size(max = 32, message = "尺码或选项长度不能超过32个字符")
    @Pattern(regexp = "[0-9]{1,3}(\\.[0-9])?", message = "选项或鞋码格式不正确")
    @DecimalMin(value = "0.1", message = "请填写有效鞋码")
    private String shoeSize;
}
