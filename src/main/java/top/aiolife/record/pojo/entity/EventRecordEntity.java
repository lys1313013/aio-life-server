package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.record.pojo.vo.FileVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 活动记录：演唱会、音乐节、技术分享等。
 *
 * @author Lys
 * @date 2025/04/04
 */
@Getter
@Setter
@TableName("event_record")
public class EventRecordEntity extends BaseEntity {

    /** 所属用户，与创建人审计字段独立。 */
    private Long userId;

    /**
     * 活动名称
     */
    private String title;

    /**
     * 演出者、主讲人或嘉宾
     */
    private String participants;

    /**
     * 活动类型(演唱会/音乐节/技术分享等)
     */
    private String eventType;

    /**
     * 活动日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    /**
     * 活动城市
     */
    private String city;

    /**
     * 活动地点
     */
    private String venue;

    /**
     * 票价或报名费
     */
    private BigDecimal ticketPrice;

    /**
     * 座位信息
     */
    private String seatInfo;

    /**
     * 活动时长(分钟)
     */
    private Integer durationMinutes;

    /**
     * 活动评分(1-5)
     */
    private Integer rating;

    /**
     * 活动评价
     */
    private String review;

    /**
     * 购票或报名平台
     */
    private String purchasePlatform;

    /**
     * 购票或报名订单号
     */
    private String orderNumber;

    /**
     * 关联文件ID列表（提交时用于绑定）
     */
    @TableField(exist = false)
    private List<String> fileIds;

    /**
     * 关联文件列表（查询时回填）
     */
    @TableField(exist = false)
    private List<FileVO> files;
}
