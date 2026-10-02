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
     * 开始时间 （分钟）包含开始时间
     */
    private Integer startTime;

    /**
     * 结束时间 （分钟） 包含结束时间
     */
    private Integer endTime;

    private String title;

    private String description;

    private Integer duration;

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
    private Long isManual;

    private String id;
}
