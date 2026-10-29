package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 观影记录查询请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/22
 */
@Data
public class MovieQueryMcpReq {

    @Description("片名模糊搜索")
    private String title;

    @Description("导演搜索")
    private String director;

    @Description("状态筛选：not_started/in_progress/completed/on_hold")
    @Schema(allowableValues = {"not_started", "in_progress", "completed", "on_hold"})
    private String status;

    @Description("页码，默认 1")
    @Min(1)
    @Schema(defaultValue = "1")
    private Integer page;

    @Description("每页条数，默认 10，最大 100")
    @Min(1) @Max(100)
    @Schema(defaultValue = "10")
    private Integer pageSize;
}
