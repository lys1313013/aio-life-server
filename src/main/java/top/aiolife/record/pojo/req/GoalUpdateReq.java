package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/** GoalUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalUpdateReq {
    /** 是否固定到首页：0=否，1=是。 */
    @Min(0)
    @Max(1)
    private Integer isPinned;


    @NotNull
    private Long id;

    /**
     * 目标类型：1=年度目标，2=月度目标，3=日目标
     *
     * @see GoalTypeEnum
     */
    private Integer type;

    /**
     * 目标标题
     */
    private String title;

    /**
     * 目标描述
     */
    private String description;

    /**
     * 目标详细内容/行动计划
     */
    private String content;

    /**
     * 目标状态：not_started/in_progress/completed/on_hold
     *
     * @see ProgressStatusEnum
     */
    private ProgressStatusEnum status;

    /**
     * 目标值（如：100本书）
     */
    private Integer targetValue;

    /**
     * 当前值（如：已完成50本）
     */
    private Integer currentValue;

    /**
     * 年份（用于年度目标筛选）
     */
    private Integer year;

    /**
     * 月份（用于月度目标筛选）
     */
    private Integer month;

    /**
     * 日期（用于日目标筛选）
     */
    private Integer day;

    /**
     * 父目标ID
     * <p>用于建立目标层级关系，如月度目标的父目标为年度目标</p>
     */
    private Long parentId;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDate;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDate;

    /**
     * 目标标签（JSON格式存储）
     */
    private String tags;
}
