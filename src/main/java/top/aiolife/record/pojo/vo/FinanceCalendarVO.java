package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收支日历；范围结束日期不包含，金额与格子来自同一次聚合查询。 */
public record FinanceCalendarVO(String granularity, String date, String period, String label,
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                @Schema(description = "结束日期，不含当天")
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate, String month,
                                @JsonFormat(pattern = "yyyy-MM-dd") LocalDate today,
                                @Schema(description = "期间金额合计") BigDecimal amount,
                                List<Bucket> buckets,
                                @Schema(description = "刷新间隔，秒") int refreshInterval) {
    @Schema(name = "FinanceCalendarBucket", description = "单个周期的收支金额")
    public record Bucket(String key, String label,
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                         @Schema(description = "结束日期，不含当天")
                         @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                         BigDecimal amount, boolean future) {}

    public record Point(LocalDate date, BigDecimal amount) {}
}
