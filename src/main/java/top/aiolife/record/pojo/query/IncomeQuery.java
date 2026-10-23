package top.aiolife.record.pojo.query;

import lombok.Data;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-30 14:10
 */
@Data
public class IncomeQuery {
     /**
     * 收入分类id
     */
    private String incTypeId;

    /**
     * 年份
     */
    private String year;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
    /** 结束日期（不包含），格式 yyyy-MM-dd。 */
    private String endTimeBefore;

    /** 仅查询未填写收入日期的记录。 */
    private Boolean undated;
}
