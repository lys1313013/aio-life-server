package top.aiolife.record.mcp.req;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;

/** 分页读取待办主任务及其明细；根据 total 继续翻页。 */
@Data
public class TaskListMcpReq {
    @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page;
    @Min(1) @Max(100)
    @Schema(description = "每页主任务数，最多 100", defaultValue = "10")
    private Integer pageSize;
}
