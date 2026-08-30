package top.aiolife.record.mcp.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 目标视图对象（仅向 AI 暴露精简字段，类型与状态返回中文标签）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class GoalMcpVO {

    /**
     * 目标 ID
     */
    private Long id;

    /**
     * 目标类型（中文）：日目标 / 周目标 / 月度目标 / 季度目标 / 半年目标 / 年度目标 / 三年目标 / 五年目标 / 十年目标 / 人生目标
     */
    private String type;

    /**
     * 目标标题
     */
    private String title;

    /**
     * 目标描述
     */
    private String description;

    /**
     * 状态（中文）：待开始 / 进行中 / 已完成 / 已放弃
     */
    private String status;

    /**
     * 目标值（可为空，表示无量化目标）
     */
    private Integer targetValue;

    /**
     * 当前值
     */
    private Integer currentValue;

    /**
     * 所属年份
     */
    private Integer year;

    /**
     * 所属月份
     */
    private Integer month;

    /**
     * 所属日期
     */
    private Integer day;

    /**
     * 父目标 ID（可为空）
     */
    private Long parentId;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDate;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDate;

    /**
     * 完成时间（状态为已完成时非空）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 目标标签
     */
    private List<String> tags;

    /**
     * 已完成子目标数量
     */
    private Integer completedCount;

    /**
     * 子目标总数
     */
    private Integer totalCount;
}
