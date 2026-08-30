package top.aiolife.record.mcp.vo;

import lombok.Data;

import java.util.Map;

/**
 * MCP B站学习统计视图对象
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class BVideoStatisticsMcpVO {

    /**
     * 已学习总时长（秒）
     */
    private int studiedSeconds;

    /**
     * 未学习总时长（秒）
     */
    private int unstudiedSeconds;

    /**
     * 视频总时长（秒）
     */
    private int totalSeconds;

    /**
     * 整体学习进度百分比（0-100，保留 1 位小数）
     */
    private double progressPercentage;

    /**
     * 各状态数量，key 为中文状态标签（未开始 / 进行中 / 已暂停 / 部分完成 / 已完成）
     */
    private Map<String, Integer> statusCounts;
}
