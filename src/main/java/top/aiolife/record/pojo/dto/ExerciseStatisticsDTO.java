package top.aiolife.record.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 运动记录统计DTO
 *
 * @author Lys
 * @date 2026-03-17
 */
@Data
public class ExerciseStatisticsDTO {

    /**
     * 运动类型ID(关联user_dict_data.id)
     */
    private Long exerciseTypeId;

    /**
     * 运动日期
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