package top.aiolife.record.mcp.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.time.LocalDate;

/**
 * MCP 时间记录保存请求体
 */
@Data
public class TimeRecordSaveMcpReq {

    @Description("分类id")
    private String categoryId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("日期，格式 yyyy-MM-dd；省略为今天。历史日期必须显式提供 startTime/endTime")
    private LocalDate date;

    @Description("标题，可省略")
    private String title;

    @Description("开始分钟（0–1439，包含该分钟）；与 endTime 一起提供，省略时追加今天末条记录之后至当前时刻")
    @Min(0) @Max(1439)
    private Integer startTime;

    @Description("结束分钟（0–1439，包含该分钟），不得早于 startTime；时长自动计算")
    @Min(0) @Max(1439)
    private Integer endTime;

    @Description("描述")
    private String description;
}
