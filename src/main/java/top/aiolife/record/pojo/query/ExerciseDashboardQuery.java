package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** 首页运动按日期游标分页，每页最多 30 天。 */
@Data
public class ExerciseDashboardQuery {
    @JsonSetter(nulls = Nulls.FAIL)
    private LocalDate lastDate;

    @NotNull @Min(1) @Max(30)
    private Integer pageSize = 7;
}
