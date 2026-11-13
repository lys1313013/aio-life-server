package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** EventRecordCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventRecordCreateReq {

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
    private List<String> fileIds;
}
