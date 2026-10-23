package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收支概览复用同一时间分桶，amount 为结余，结束日期不包含。 */
public record FinanceOverviewCalendarVO(String granularity, String date, String period, String label,
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate, String month,
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate today,
                                       BigDecimal amount, BigDecimal income, BigDecimal expense, BigDecimal balance,
                                       List<Bucket> buckets, int refreshInterval) {
    public record Bucket(String key, String label,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                         BigDecimal amount, boolean future,
                         BigDecimal income, BigDecimal expense, BigDecimal balance) {}
}
