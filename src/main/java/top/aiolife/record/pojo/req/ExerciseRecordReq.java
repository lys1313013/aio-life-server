package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.time.LocalDate;

/**
 * 练习记录请求类
 *
 * @author Lys
 * @date 2026/04/26
 */
@Data
public class ExerciseRecordReq {

    /**
     * 主键ID
     */
    @Description("主键ID，更新时必传")
    private String id;

    /**
     * 运动类型ID(关联user_dict_data.id)
     */
    @Description("运动类型ID")
    private Long exerciseTypeId;

    /**
     * 运动日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("运动日期，格式：yyyy-MM-dd")
    private LocalDate exerciseDate;

    /**
     * 运动次数
     */
    @Description("运动次数")
    private Integer exerciseCount;

    /**
     * 运动描述
     */
    @Description("运动描述")
    private String description;
}
