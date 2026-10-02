package top.aiolife.record.pojo.query;

import lombok.Data;

/** 运动统计仅接受运动类型和日期区间。 */
@Data
public class ExerciseStatisticsQuery {
    private Long exerciseTypeId;
    private String startDate;
    private String endDate;

    public boolean hasFilters() {
        return exerciseTypeId != null || (startDate != null && !startDate.isBlank())
                || (endDate != null && !endDate.isBlank());
    }
}
