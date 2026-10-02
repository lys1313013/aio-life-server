package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

@Data
public class MovieVO {
    private String id;
    private String title;
    private Integer type;
    private String director;
    private String url;
    private String fileId;
    private String name;
    private ProgressStatusEnum status;
    private Integer totalProgress;
    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private Integer rating;

    private String remark;
}
