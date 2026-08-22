package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

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

    @Description("状态筛选：0-想看，1-在看，2-看过，3-搁置")
    private Integer status;

    @Description("页码，默认 1")
    private Integer page;

    @Description("每页条数，默认 10，最大 100")
    private Integer size;
}