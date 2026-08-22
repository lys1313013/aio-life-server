package top.aiolife.record.mcp.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 观影记录视图对象（仅向 AI 暴露精简字段，状态返回中文标签）
 *
 * @author Lys
 * @date 2026/08/22
 */
@Data
public class MovieMcpVO {

    /**
     * 观影记录 ID
     */
    private Long id;

    /**
     * 片名
     */
    private String title;

    /**
     * 导演
     */
    private String director;

    /**
     * 类型
     */
    private Integer type;

    /**
     * 状态（中文）：想看 / 在看 / 看过 / 搁置
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