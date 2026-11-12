package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收支概览复用同一时间分桶，amount 为结余，结束日期不包含。 */
public record FinanceOverviewCalendarVO(String granularity, String date, String period, String label,
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                       @Schema(description = "结束日期，不含当天")
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate, String month,
                                       @JsonFormat(pattern = "yyyy-MM-dd") LocalDate today,
                                       @Schema(description = "期间结余，收入减支出") BigDecimal amount,
                                       @Schema(description = "期间收入") BigDecimal income,
                                       @Schema(description = "期间支出") BigDecimal expense,
                                       @Schema(description = "期间结余，同 amount") BigDecimal balance,
                                       List<Bucket> buckets,
                                       @Schema(description = "刷新间隔，秒") int refreshInterval) {
    @Schema(name = "FinanceOverviewCalendarBucket", description = "单个周期的收入、支出和结余")
    public record Bucket(String key, String label,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                         @Schema(description = "结束日期，不含当天")
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                         @Schema(description = "周期结余") BigDecimal amount, boolean future,
                         @Schema(description = "周期收入") BigDecimal income,
                         @Schema(description = "周期支出") BigDecimal expense,
                         @Schema(description = "周期结余，同 amount") BigDecimal balance) {}
}
