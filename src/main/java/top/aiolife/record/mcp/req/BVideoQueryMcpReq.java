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

    @Description("状态筛选：1-未开始，2-进行中，3-已暂停，4-部分完成，5-已完成")
    private Integer status;

    @Description("页码，默认 1")
    private Integer page;

    @Description("每页条数，默认 10，最大 100")
    private Integer size;
}
