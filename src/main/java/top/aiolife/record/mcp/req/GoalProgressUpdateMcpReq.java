package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 目标进展更新请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class GoalProgressUpdateMcpReq {

    @Description("目标 ID（应优先通过 goal_query 获取，须属于当前用户）")
    private Long goalId;

    @Description("更新当前值；与 status 至少传一个")
    private Integer currentValue;

    @Description("状态：进行中 / 已完成 / 已放弃")
    private String status;
}
