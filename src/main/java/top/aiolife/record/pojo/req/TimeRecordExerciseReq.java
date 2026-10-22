package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** TimeRecordExerciseReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordExerciseReq {
    /** 已有运动记录 ID；新增明细不传。 */
    private String id;
    @Positive(message = "运动类型ID必须为正数")
    private Long exerciseTypeId;
    @PositiveOrZero(message = "运动数量不能为负数")
    private Integer exerciseCount;
    private String description;
}
