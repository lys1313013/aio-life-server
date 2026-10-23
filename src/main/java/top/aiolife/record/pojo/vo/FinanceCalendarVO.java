package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收支日历；范围结束日期不包含，金额与格子来自同一次聚合查询。 */
public record FinanceCalendarVO(String granularity, String date, String period, String label,
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate, String month,
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate today,
                                BigDecimal amount, List<Bucket> buckets, int refreshInterval) {
    public record Bucket(String key, String label,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                         BigDecimal amount, boolean future) {}

    public record Point(LocalDate date, BigDecimal amount) {}
}
