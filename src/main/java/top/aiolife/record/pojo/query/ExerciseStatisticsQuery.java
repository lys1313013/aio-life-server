package top.aiolife.record.pojo.query;

import lombok.Data;

/** 运动统计支持与列表一致的运动类型、日期区间和备注筛选。 */
@Data
public class ExerciseStatisticsQuery {
    private Long exerciseTypeId;
    private String startDate;
    private String endDate;
    private String description;

    public boolean hasFilters() {
        return exerciseTypeId != null || (startDate != null && !startDate.isBlank())
                || (endDate != null && !endDate.isBlank())
                || (description != null && !description.isBlank());
    }
}
