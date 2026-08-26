package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("movie")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MovieEntity extends BaseEntity {

    private String title;

    private Integer type;

    private String director;

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

    private Integer status;

    private Integer totalProgress;

    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private String remark;

    private Long userId;
}
