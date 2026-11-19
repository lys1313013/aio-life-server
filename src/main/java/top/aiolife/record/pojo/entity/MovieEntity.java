package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

@Getter
@Setter
@ToString
@TableName("movie")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MovieEntity extends BaseEntity {
    private String title;

    private Integer type;

    private String director;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String url;

    @JsonIgnore
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String platform;

    @JsonIgnore
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String externalKey;

    /**
     * 图片文件ID
     */
    /** 从 file 业务关联填充，不映射业务表字段。 */
    @TableField(exist = false)
    private String fileId;

    /**
     * 临时图片链接，仅供前端显示，不持久化
     */
    @TableField(exist = false)
    private String coverImgUrl;

    private ProgressStatusEnum status;

    private Integer totalProgress;

    private Integer currentProgress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate finishDate;

    /**
     * 个人评分：1-5
     */
    private Integer rating;

    private String remark;

    private Long userId;
}
