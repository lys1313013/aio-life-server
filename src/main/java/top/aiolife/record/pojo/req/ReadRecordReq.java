package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDateTime;

@Data
public class ReadRecordReq {
    private Long id;
    private String title;
    private Integer type;
    private String author;
    private String url;
    private String fileId;
    private String coverImgUrl;
    private ProgressStatusEnum status;
    private Integer totalProgress;
    private Integer currentProgress;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
    
    private String remark;
}
