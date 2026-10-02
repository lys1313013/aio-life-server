package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
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
}
