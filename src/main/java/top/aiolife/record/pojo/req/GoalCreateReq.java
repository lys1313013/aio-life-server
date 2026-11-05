package top.aiolife.record.pojo.req;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/** GoalCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalCreateReq {

    /** 进度来源：manual 手动，graphql 自定义只读查询。 */
    @Size(max = 16)
    private String progressMode;
    /** 自定义 GraphQL 查询文本。 */
    @Size(max = 32768)
    private String progressQuery;
    /** GraphQL 固定变量，JSON 对象文本。 */
    @Size(max = 16384)
    private String progressVariables;
    /** 变量到目标上下文的绑定，JSON 对象文本。 */
    @Size(max = 16384)
    private String progressVariableBindings;
    /** 相对于 GraphQL data 的数值路径，支持对象字段和数组下标。 */
    @Size(max = 256)
    private String progressValuePath;
    /** 达标比较：gte 达到，gt 超过。 */
    @Size(max = 8)
    private String targetOperator;

    /** 是否固定到首页：0=否，1=是。 */
    @Min(0)
    @Max(1)
    private Integer isPinned;


    /**
     * 目标类型：1=日目标，2=周目标，3=月度目标，4=季度目标，5=半年目标，6=年度目标，7=三年目标，8=五年目标，9=十年目标，10=人生目标
     *
     * @see GoalTypeEnum
     */
    @Schema(implementation = GoalTypeEnum.class)
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
    @DecimalMin("0")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal targetValue;

    /**
     * 当前值（如：已完成50本）
     */
    @DecimalMin("0")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal currentValue;

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
