package top.aiolife.record.pojo.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 首页阅读统计卡片只查询当前周期，不接收历史日期。 */
@Data
public class WereadStatsQuery {
    @NotBlank
    @Pattern(regexp = "week|month|year", message = "统计粒度仅支持 week、month、year")
    @Schema(description = "统计粒度：week、month、year", requiredMode = Schema.RequiredMode.REQUIRED)
    private String granularity;
}
