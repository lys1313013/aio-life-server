package top.aiolife.record.pojo.vo;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TimeRecord VO
 *
 * @author Lys
 * @date 2026-02-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TimeRecordVO extends TimeRecordListVO {
    /**
     * 运动记录列表
     */
    private List<ExerciseRecordVO> exercises;
}
