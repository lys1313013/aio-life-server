package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;

/**
 * 首页运动汇总 - 每日按运动类型聚合的子项
 *
 * @author Lys
 * @date 2026/06/14
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseDashboardItemVO {

    /**
     * 运动类型ID（与 user_dict_data.id 一致）
     */
    private Long exerciseTypeId;

    /**
     * 运动类型名称
     */
    private String typeLabel;

    /**
     * 图标（Iconify 格式）
     */
    private String icon;

    /**
     * 主题色（Hex）
     */
    private String color;

    /**
     * 当日该类型运动总次数
     */
    private Integer count;

    /**
     * 上一次做该类型运动的日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate prevDate;

    /**
     * 上一次的次数；为 null 表示首次记录
     */
    private Integer prevCount;

    /**
     * 差值（本次 - 上次）；为 null 表示首次记录
     */
    private Integer deltaCount;

    /**
     * 差值百分比（(本次 - 上次) / 上次 * 100），四舍五入取整；为 null 表示首次记录或上次为 0
     */
    private Integer deltaPercent;
}