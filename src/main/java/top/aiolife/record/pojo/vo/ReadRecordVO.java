package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDateTime;

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
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
    
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
