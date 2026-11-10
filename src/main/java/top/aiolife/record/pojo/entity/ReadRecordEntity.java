package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

@Getter
@Setter
@ToString
@TableName("read_record")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadRecordEntity extends BaseEntity {

    private String title;

    private Integer type;

    private String author;

    private String url;

    /**
     * 图片文件ID
     */
    private String fileId;

    /**
     * 临时图片链接，仅供前端显示，不持久化
     */
    @TableField(exist = false)
    private String coverImgUrl;

    private ProgressStatusEnum status;

    private Integer totalProgress;

    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private String remark;

    private Long userId;
}
