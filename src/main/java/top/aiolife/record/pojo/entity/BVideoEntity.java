package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/06 22:48
 */
@Getter
@Setter
@ToString
@TableName("b_video")
public class BVideoEntity extends BaseEntity{
    /** 从 file 业务关联填充，不映射业务表字段。 */
    @TableField(exist = false)
    private String coverFileId;
    @TableField(exist = false)
    private String coverState;


    /**
     * 视频标题
     */
    private String title;

    /**
     * B站视频URL
     */
    private String url;

    /**
     * 视频封面URL
     */
    @TableField(exist = false)
    private String cover;

    /**
     * 视频时长（单位秒）
     */
    private Integer durationSeconds;

    /**
     * 已观看时长（秒）
     */
    private Integer watchedDurationSeconds;

    /**
     * 总集数
     */
    private Integer episodes = 1;

    /**
     * 当前观看集数
     */
    private Integer currentEpisode = 1;

    /**
     * 学习状态
     */
    private ProgressStatusEnum status;

    /**
     * 最后观看时间
     */
    private LocalDateTime lastWatched;

    /**
     * 学习笔记
     */
    private String notes;

    /**
     * BV号
     */
    private String bvid;

    /**
     * AV号
     */
    private String aid;

    /**
     * 视频描述
     */
    private String description;

    /**
     * UP主信息（JSON格式存储）
     */
    private String ownerName;

    private Long userId;

    /**
     * 分集信息（JSON格式存储）
     */
    private String pagesInfo;
}
