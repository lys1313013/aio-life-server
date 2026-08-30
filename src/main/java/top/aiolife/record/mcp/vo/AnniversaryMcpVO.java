package top.aiolife.record.mcp.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * MCP 纪念日视图对象（type 返回中文标签，daysRemaining 由后端计算）
 *
 * @author Lys
 * @date 2026/08/30
 */
@Data
public class AnniversaryMcpVO {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 类型（中文）：纪念日 / 倒数日
     */
    private String type;

    /**
     * 目标日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    /**
     * 备注
     */
    private String note;

    /**
     * 距今天数：正数=还有 N 天，负数=已过 N 天；纪念日按「下一次发生」计算，倒数日按目标日期直接计算
     */
    private Long daysRemaining;
}
