package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import lombok.Data;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/** GoalVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalVO {

    /** 进度来源：manual 手动，graphql 自定义只读查询。 */
    private String progressMode;
    /** 自定义 GraphQL 查询文本。 */
    private String progressQuery;
    /** GraphQL 固定变量，JSON 对象文本。 */
    private String progressVariables;
    /** 变量到目标上下文的绑定，JSON 对象文本。 */
    private String progressVariableBindings;
    /** 相对于 GraphQL data 的数值路径，支持对象字段和数组下标。 */
    private String progressValuePath;
    /** 达标比较：gte 达到，gt 超过。 */
    private String targetOperator;

    /** 是否固定到首页：0=否，1=是。 */
    private Integer isPinned;

    /** 首页固定顺序，越小越靠前。 */
    private Integer pinnedSortOrder;


    private Long id;

    /**
     * 目标类型：1=日，2=周，3=月，4=季度，5=半年，6=年度，7=三年，8=五年，9=十年，10=人生
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
    private BigDecimal targetValue;

    /**
     * 当前值（如：已完成50本）
     */
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
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 目标标签（JSON格式存储）
     */
    private String tags;

    /**
     * 已完成子目标数量（非数据库字段）
     */
    private Integer completedCount;

    /**
     * 子目标总数（非数据库字段）
     */
    private Integer totalCount;
    /** 最近成功计算时间；失败时保留上次成功值和时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime progressCheckedAt;
    /** 最近计算错误；NULL 表示无错误。 */
    private String progressError;
    /** 目标配置修订号，用于拒绝编辑期间过期的计算结果。 */
    private Long progressVersion;
}
