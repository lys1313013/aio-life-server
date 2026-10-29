package top.aiolife.record.mcp.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * 时间记录日期范围查询请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/04/26
 */
@Data
public class TimeRecordDateRangeMcpReq {

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("开始日期，格式：yyyy-MM-dd")
    @NotNull
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("结束日期，格式：yyyy-MM-dd")
    @NotNull
    private LocalDate endDate;
}
