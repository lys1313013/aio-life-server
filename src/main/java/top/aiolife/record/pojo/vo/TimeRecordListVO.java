package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Data;

/** TimeRecordListVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordListVO {

    /**
     * 分类id
     */
    private Long categoryId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * 开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer startMinute;

    /**
     * 结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer endMinute;

    private String title;

    private String description;

    /**
     * 时长（分钟），闭区间 endMinute - startMinute + 1
     */
    private Integer durationMinutes;

    /**
     * 关联业务类型，对应 RelateTypeEnum
     */
    private Integer relateType;

    /**
     * 关联业务ID
     */
    private Long relateId;

    /**
     * 是否手动添加
     */
    private Integer isManual;

    private String id;
}
