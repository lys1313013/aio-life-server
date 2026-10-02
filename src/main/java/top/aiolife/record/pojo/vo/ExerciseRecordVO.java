package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Data;

/** ExerciseRecordVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecordVO {

    private Long id;

    /**
     * 运动类型ID(关联user_dict_data.id)
     */
    private Long exerciseTypeId;

    /**
     * 运动数量
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate exerciseDate;

    /**
     * 运动次数
     */
    private Integer exerciseCount;

    /**
     * 运动描述
     */
    private String description;

    /**
     * 时间 ID
     */
    private String timeId;
}
