package top.aiolife.record.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 首页阅读统计卡片：只返回柱图与汇总所需的聚合值。 */
public record WereadStatsVO(
        boolean connected,
        String granularity,
        String periodStart,
        String periodEnd,
        int elapsedDays,
        List<Bucket> buckets,
        int totalSeconds,
        int dailyAverageSeconds,
        String previousPeriodStart,
        String previousPeriodEnd,
        int previousTotalSeconds,
        int refreshInterval) {

    /** 周为每天、月为每天、年为每月；缺失周期补 0。 */
    @Schema(name = "WereadStatsBucket", description = "单个周期的阅读时长")
    public record Bucket(String key, String label, @Schema(description = "阅读时长，秒") int seconds) {}
}
