package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/** BVideoVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BVideoVO {

    private Long id;

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

    private Integer episodes;

    private Integer currentEpisode;

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

    /**
     * 分集信息（JSON格式存储）
     */
    private String pagesInfo;
}
