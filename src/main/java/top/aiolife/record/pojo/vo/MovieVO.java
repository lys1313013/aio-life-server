package top.aiolife.record.pojo.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

@Data
public class MovieVO implements RankedWorkView {
    private Long creativeWorkId;
    private String coverImgUrl;
    private List<RankingBadgeVO> rankings = List.of();
    private String id;
    private String title;
    private Long retryCoverWorkId;
    private Integer type;
    private String director;
    private String url;
    @JsonIgnore
    private String platform;
    @JsonIgnore
    private String externalKey;
    private String fileId;
    private String name;
    private ProgressStatusEnum status;
    private Integer totalProgress;
    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate finishDate;

    private Integer rating;

    private String remark;
}
