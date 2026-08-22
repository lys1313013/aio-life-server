package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;

/**
 * 运动记录实体类
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
@Data
@TableName("exercise_record")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecordEntity extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

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