package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

@Data
public class ReadRecordVO {
    private String id;
    private String title;
    private Integer type;
    private String author;
    private String url;
    private String fileId;
    private ProgressStatusEnum status;
    private Integer totalProgress;
    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate finishDate;

    private String remark;
}
