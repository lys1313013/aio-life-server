package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 时间记录查询结果 VO（日期范围查询专用）
 * startMinute 和 endMinute 为当天 0:00 起的分钟数，闭区间
 *
 * @author Lys
 * @date 2026-04-26
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordDateRangeVO {
    private String id;
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * 开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer startMinute;

    /**
     * 结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer endMinute;

    private String title;

    /**
     * 运动明细，仅包含运动名称和运动次数。
     */
    private List<TimeRecordExerciseVO> exercises;
}
