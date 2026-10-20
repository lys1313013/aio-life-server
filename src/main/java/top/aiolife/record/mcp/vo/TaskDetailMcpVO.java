package top.aiolife.record.mcp.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 任务明细视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailMcpVO {

    private Long id;

    private String content;

    /**
     * 是否完成: 0-未完成, 1-已完成
     */
    private Integer isCompleted;

    /** 优先级：1-高、10-中、20-低。 */
    private Integer priority;

    /** 是否星标/置顶：0-未置顶、1-已置顶。 */
    private Integer isStarred;
}
