package top.aiolife.record.mcp.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MCP 目标分页视图对象
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
@Builder
public class GoalPageMcpVO {

    /**
     * 目标列表
     */
    private List<GoalMcpVO> records;

    /**
     * 总条数
     */
    private Long total;
}
