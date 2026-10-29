package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 目标进展更新请求（AI接口专用）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class GoalProgressUpdateMcpReq {

    @Description("目标 ID（应优先通过 goal_query 获取，须属于当前用户）")
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    private String goalId;

    @Description("更新当前值；与 status 至少传一个")
    private Integer currentValue;

    @Description("状态：in_progress=进行中 / completed=已完成 / on_hold=搁置")
    @Schema(allowableValues = {"in_progress", "completed", "on_hold"})
    private String status;
}
