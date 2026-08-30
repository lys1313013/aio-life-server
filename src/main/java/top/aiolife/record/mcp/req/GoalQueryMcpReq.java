package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 目标查询请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class GoalQueryMcpReq {

    @Description("目标类型筛选：1-日目标，2-周目标，3-月度目标，4-季度目标，5-半年目标，6-年度目标，7-三年目标，8-五年目标，9-十年目标，10-人生目标")
    private Integer type;

    @Description("状态筛选：0-待开始，1-进行中，2-已完成，3-已放弃")
    private Integer status;

    @Description("关键词，模糊匹配标题/描述/标签")
    private String keyword;

    @Description("年份筛选（用于年度/月度目标）")
    private Integer year;

    @Description("月份筛选（用于月度目标，需配合 year）")
    private Integer month;

    @Description("页码，默认 1")
    private Integer page;

    @Description("每页条数，默认 10，最大 100")
    private Integer size;
}
