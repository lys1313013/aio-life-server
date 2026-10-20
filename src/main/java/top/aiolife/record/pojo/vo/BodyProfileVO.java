package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BodyProfileVO {
    private Long id;
    private Integer version;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    private BigDecimal bmi;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bodyFatPercent;
    private BigDecimal shoulderWidthCm;
    private BigDecimal chestCm;
    private BigDecimal upperArmCm;
    private BigDecimal forearmCm;
    private BigDecimal waistCm;
    private BigDecimal hipCm;
    private BigDecimal thighCm;
    private BigDecimal calfCm;
    private BigDecimal inseamCm;
    private String gender;
    private String measurementSide;
    private String armState;
    private String shoeSystem;
    private String topSize;
    private String trousersSize;
    private String shoeSize;
}
