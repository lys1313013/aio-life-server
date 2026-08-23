package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/06 22:48
 */
@Data
@TableName("b_video")
public class BVideoEntity extends BaseEntity{

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
    private String cover;

    /**
     * 视频时长（单位秒）
     */
    private Integer duration;

    /**
     * 观看时长
     */
    private Integer watchedDuration;

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
    private Integer status;

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