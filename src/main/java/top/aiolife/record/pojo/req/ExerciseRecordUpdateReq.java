package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Data;

/** ExerciseRecordUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecordUpdateReq {

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
}
