package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

/** ExerciseRecordQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecordQuery {

    /**
     * 运动类型ID(关联user_dict_data.id)
     */
    private Long exerciseTypeId;
    @Schema(description = "开始日期，yyyy-MM-dd，含当天")
    private LocalDate startDate;
    @Schema(description = "结束日期，yyyy-MM-dd，含当天")
    private LocalDate endDate;
    @Schema(description = "运动备注关键词")
    private String description;
}
