package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Pattern;
import top.aiolife.core.id.AttachmentKey;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDate;

@Data
public class MovieReq {
    private Long id;
    private String title;
    private Integer type;
    private String director;
    private String url;
    @Pattern(regexp = AttachmentKey.PATTERN)
    private String fileId;
    private String coverImgUrl;
    private ProgressStatusEnum status;
    private Integer totalProgress;
    private Integer currentProgress;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate finishDate;

    private Integer rating;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean ratingProvided;

    @JsonSetter("rating")
    public void setRating(Integer rating) {
        this.rating = rating;
        this.ratingProvided = true;
    }

    @JsonIgnore
    public boolean isRatingProvided() { return ratingProvided; }

    private String remark;
}
