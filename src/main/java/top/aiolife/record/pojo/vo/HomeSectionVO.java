package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 首页业务摘要：只暴露卡片展示所需字段，不承载业务编辑数据。 */
public final class HomeSectionVO {
    private HomeSectionVO() {}

    public record Items<T>(List<T> items, int refreshInterval) {}
    @Data
    public static class Memo {
        private Long id;
        private String title;
        private Boolean hiddenContent;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }

    @Data
    public static class Video {
        private Long id;
        private String coverFileId;
        private String coverState;
        private String title;
        private Integer currentEpisode;
        private Integer episodes;
        private Integer watchedDuration;
        private Integer duration;
        private Integer progressPercent;
        private String resumeUrl;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastWatched;
    }

    @Data
    public static class Device {
        private Long id;
        private String fileId;
        private String name;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate purchaseDate;
        private BigDecimal purchasePrice;
        private Integer usageDays;
        private BigDecimal dailyCost;
    }
}
