package top.aiolife.record.pojo.req;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 时间记录请求体类
 *
 * @author Lys
 * @date 2026-02-22 17:34
 */
@Data
@TableName("time_record")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordReq {

    /**
     * 主键ID
     */
    @TableId
    @Description("主键ID")
    private String id;

    /**
     * 分类id
     */
    @Description("分类id")
    private String categoryId;

    /**
     * 日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("日期，格式：yyyy-MM-dd")
    private LocalDate date;

    /**
     * 开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    @Description("开始分钟（从 0:00 开始的分钟数，0–1439，包含该分钟）")
    private Integer startMinute;
    /**
     * 结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    @Description("结束分钟（从 0:00 开始的分钟数，0–1439，包含该分钟）")
    private Integer endMinute;
    /**
     * 标题
     */
    @Description("标题")
    private String title;
    /**
     * 描述
     */
    @Description("描述")
    private String description;
    /**
     * 关联业务类型，对应 RelateTypeEnum
     */
    @Description("关联业务类型：1-阅读，2-观影")
    private Integer relateType;

    /**
     * 关联业务ID
     */
    @Description("关联业务ID")
    private Long relateId;

    /**
     * 关联的练习记录
     */
    @Description("关联的练习记录列表")
    List<ExerciseRecordReq> exercises;
}
