package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 纪念日查询请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class AnniversaryQueryMcpReq {

    @Description("类型筛选：纪念日 / 倒数日")
    private String type;

    @Description("仅返回 N 天内即将到来的记录（纪念日按 targetDate 的月-日匹配，跨年生效；倒数日按目标日期直接计算），默认不过滤")
    private Integer withinDays;
}
