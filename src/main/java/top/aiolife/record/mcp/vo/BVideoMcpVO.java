package top.aiolife.record.mcp.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP B站学习视频视图对象（仅向 AI 暴露精简字段，状态返回中文标签）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class BVideoMcpVO {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 视频标题
     */
    private String title;

    /**
     * UP 主
     */
    private String ownerName;

    /**
     * BV 号
     */
    private String bvid;

    /**
     * 状态（中文）：未开始 / 进行中 / 已暂停 / 部分完成 / 已完成
     */
    private String status;

    /**
     * 视频总时长（秒）
     */
    private Integer duration;

    /**
     * 已观看时长（秒）
     */
    private Integer watchedDuration;

    /**
     * 总集数
     */
    private Integer episodes;

    /**
     * 当前观看集数
     */
    private Integer currentEpisode;

    /**
     * 观看进度百分比（0-100，保留 1 位小数）
     */
    private Double progressPercentage;

    /**
     * 最后观看时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastWatched;

    /**
     * 学习笔记
     */
    private String notes;
}
