package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import lombok.Data;

/** TimeRecordExerciseReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordExerciseReq {
    private Long exerciseTypeId;
    private Integer exerciseCount;
    private String description;
}
