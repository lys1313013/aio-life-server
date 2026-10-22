package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** ExerciseRecordCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecordCreateReq {

    /**
     * 运动类型ID(关联user_dict_data.id)
     */
    @NotNull(message = "运动类型不能为空")
    @Positive(message = "运动类型ID必须为正数")
    private Long exerciseTypeId;

    /**
     * 运动数量
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "运动日期不能为空")
    private LocalDate exerciseDate;

    /**
     * 运动次数
     */
    @PositiveOrZero(message = "运动数量不能为负数")
    private Integer exerciseCount;

    /**
     * 运动描述
     */
    private String description;
}
