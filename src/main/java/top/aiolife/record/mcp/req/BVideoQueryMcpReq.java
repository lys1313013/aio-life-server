package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * B站学习视频查询请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class BVideoQueryMcpReq {

    @Description("标题模糊搜索")
    private String title;

    @Description("状态筛选：not_started/in_progress/on_hold/completed")
    private String status;

    @Description("页码，默认 1")
    private Integer page;

    @Description("每页条数，默认 10，最大 100")
    private Integer size;
}
