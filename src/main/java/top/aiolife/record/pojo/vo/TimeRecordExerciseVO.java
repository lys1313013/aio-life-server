package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间记录日期范围查询中的运动明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordExerciseVO {

    /**
     * 运动名称。
     */
    private String exerciseName;

    /**
     * 运动次数。
     */
    private Integer exerciseCount;
}
