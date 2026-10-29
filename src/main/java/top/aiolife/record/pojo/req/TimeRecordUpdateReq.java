package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.model.output.structured.Description;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新时迹。未提交的起止分钟保留原值；合并后结束不得早于开始，时长由服务端计算。
 *
 * @author Lys
 * @date 2026-02-22 17:34
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordUpdateReq {

    /**
     * 时迹分类 ID，从时迹分类查询接口获取；省略保留原分类。
     */
    @Description("分类id")
    private String categoryId;

    /**
     * 记录日期，省略保留原日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("日期，格式：yyyy-MM-dd")
    private LocalDate date;

    /**
     * 开始时间 （分钟）包含开始时间
     */
    @Description("开始时间（从 0:00 开始的分钟数）")
    @Min(0) @Max(1439)
    private Integer startTime;
    /**
     * 结束时间 （分钟） 包含结束时间
     */
    @Description("结束时间（从 0:00 开始的分钟数）")
    @Min(0) @Max(1439)
    private Integer endTime;
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
     * 关联业务类型：1=阅读，2=观影；与 relateId 一起提交。
     */
    @Description("关联业务类型：1-阅读，2-观影")
    @Schema(allowableValues = {"1", "2"})
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
    @Valid
    List<TimeRecordExerciseReq> exercises;
}
