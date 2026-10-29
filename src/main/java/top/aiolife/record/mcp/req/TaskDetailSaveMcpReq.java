package top.aiolife.record.mcp.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import dev.langchain4j.model.output.structured.Description;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * MCP 任务明细保存请求体
 */
@Data
public class TaskDetailSaveMcpReq {

    @Description("主任务ID")
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]*$")
    private String taskId;

    @Description("明细内容")
    @NotBlank
    private String content;

    @Description("优先级: 1-高, 10-中, 20-低。如果不传默认10")
    @Schema(allowableValues = {"1", "10", "20"})
    private Integer priority;

    @Description("是否关注/收藏: 0-未关注, 1-已关注。如果不传默认0")
    @Min(0)
    @Max(1)
    private Integer isStarred;
    @JsonIgnore
    @AssertTrue(message = "priority 仅支持 1（高）、10（中）、20（低）")
    public boolean isPriorityValid() {
        return priority == null || List.of(1, 10, 20).contains(priority);
    }
}
