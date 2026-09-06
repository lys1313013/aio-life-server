package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import top.aiolife.record.pojo.enums.GoalTypeEnum;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.time.LocalDateTime;

/**
 * 目标实体
 *
 * <p>支持年度目标、月度目标、日目标的多层级目标管理</p>
 *
 * @author Lys
 * @date 2026/03/30
 * @see GoalTypeEnum
 * @see ProgressStatusEnum
 */
@Data
@TableName("goal")
public class GoalEntity {

    /**
     * 目标ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 目标标签（JSON格式存储）
     */
    private String tags;

    /**
     * 是否删除：0=未删除，1=已删除
     */
    private Integer isDeleted;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 创建人ID
     */
    private Long createUser;

    /**
     * 更新人ID
     */
    private Long updateUser;

    /**
     * 已完成子目标数量（非数据库字段）
     */
    @TableField(exist = false)
    private Integer completedCount;

    /**
     * 子目标总数（非数据库字段）
     */
    @TableField(exist = false)
    private Integer totalCount;
}
