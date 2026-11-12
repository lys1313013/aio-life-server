package top.aiolife.record.pojo.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 运动统计支持与列表一致的运动类型、日期区间和备注筛选。 */
@Data
public class ExerciseStatisticsQuery {
    @Schema(description = "运动类型 ID，关联用户字典")
    private Long exerciseTypeId;
    @Schema(description = "开始日期，yyyy-MM-dd，含当天")
    private String startDate;
    @Schema(description = "结束日期，yyyy-MM-dd，含当天")
    private String endDate;
    @Schema(description = "运动备注关键词")
    private String description;

    public boolean hasFilters() {
        return exerciseTypeId != null || (startDate != null && !startDate.isBlank())
                || (endDate != null && !endDate.isBlank())
                || (description != null && !description.isBlank());
    }
}
