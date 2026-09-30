package top.aiolife.record.pojo.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 时迹日期范围筛选，日期格式为 yyyy-MM-dd。
 *
 * @author Lys
 * @date 2025-11-02 17:45
 */
@Data
public class TimeWeekQuery {
    /** 起始日期，格式 yyyy-MM-dd。 */
    private LocalDate startDate;
    /** 结束日期，格式 yyyy-MM-dd。 */
    private LocalDate endDate;
}
