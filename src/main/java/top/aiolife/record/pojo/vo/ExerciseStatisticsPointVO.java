package top.aiolife.record.pojo.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 一个运动日内同一类型的完整聚合，不返回原始记录。 */
@Data
public class ExerciseStatisticsPointVO {
    private Long exerciseTypeId;
    private LocalDate exerciseDate;
    /** 同一天同类型的运动量之和；使用十进制数避免整数累加溢出。 */
    private BigDecimal exerciseCount;
    private Integer recordCount;
}
