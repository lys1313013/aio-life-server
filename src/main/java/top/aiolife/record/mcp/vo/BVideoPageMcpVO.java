package top.aiolife.record.mcp.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MCP B站学习视频分页视图对象
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
@Builder
public class BVideoPageMcpVO {

    /**
     * 视频列表
     */
    private List<BVideoMcpVO> records;

    /**
     * 总条数
     */
    private Long total;
}
