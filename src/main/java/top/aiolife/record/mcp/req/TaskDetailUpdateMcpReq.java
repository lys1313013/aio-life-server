package top.aiolife.record.mcp.req;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/** MCP 任务明细部分更新请求；未传或为 null 的字段保持原值。 */
@Data
public class TaskDetailUpdateMcpReq {

    @Description("必填，任务明细ID字符串，通过 task_list 获取，须属于当前用户")
    private String id;

    @Description("优先级：1-高、10-中、20-低；不传保持原值")
    private Integer priority;

    @Description("是否星标/置顶：0-取消、1-置顶；不传保持原值")
    private Integer isStarred;

    @Description("是否完成：0-未完成（恢复待办）、1-已完成；不传保持原值")
    private Integer isCompleted;
}
