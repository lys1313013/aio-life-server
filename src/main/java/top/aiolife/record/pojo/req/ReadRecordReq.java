package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDateTime;

@Data
public class ReadRecordReq {
    @Schema(description = "阅读记录 ID，从新增响应或阅读查询获得")
    private Long id;
    @Schema(description = "书名或文章标题")
    private String title;
    @Schema(description = "阅读类型：1=书籍，2=文章或网页", allowableValues = {"1", "2"})
    private Integer type;
    @Schema(description = "作者")
    private String author;
    @Schema(description = "原文或豆瓣书籍条目地址；同一用户的相同豆瓣条目只保存一次")
    private String url;
    @Schema(description = "已上传的封面文件 ID；通过文件上传接口获取")
    private String fileId;
    @Schema(description = "待导入的封面图片 URL；服务端上传为文件后保存 fileId")
    private String coverImgUrl;
    @Schema(description = "阅读状态：not_started=想读，in_progress=在读，completed=读完，on_hold=搁置")
    private ProgressStatusEnum status;
    @Schema(description = "总页数或总进度；与 currentProgress 使用同一单位，百分比口径时填 100")
    private Integer totalProgress;
    @Schema(description = "当前页数或进度；与 totalProgress 使用同一单位")
    private Integer currentProgress;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始阅读的本地时间；状态为在读且未提供时由服务端补当前时间")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "读完的本地时间；状态为读完且未提供时由服务端补当前时间")
    private LocalDateTime finishTime;
    
    @Schema(description = "阅读备注")
    private String remark;
}
