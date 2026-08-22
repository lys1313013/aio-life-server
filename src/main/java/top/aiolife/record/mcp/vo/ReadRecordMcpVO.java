package top.aiolife.record.mcp.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 阅读记录视图对象（仅向 AI 暴露精简字段，状态返回中文标签）
 *
 * @author Lys
 * @date 2026/08/22
 */
@Data
public class ReadRecordMcpVO {

    /**
     * 阅读记录 ID
     */
    private Long id;

    /**
     * 书名
     */
    private String title;

    /**
     * 作者
     */
    private String author;

    /**
     * 类型
     */
    private Integer type;

    /**
     * 状态（中文）：未开始 / 进行中 / 已完成 / 搁置
     */
    private String status;

    /**
     * 总进度
     */
    private Integer totalProgress;

    /**
     * 当前进度
     */
    private Integer currentProgress;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    /**
     * 备注
     */
    private String remark;
}