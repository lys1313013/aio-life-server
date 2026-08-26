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
 * 演出记录
 *
 * @author Lys
 * @date 2025/04/04
 */
@Getter
@Setter
@TableName("performance")
public class PerformanceEntity extends BaseEntity {

    /**
     * 演出名称
     */
    private String performanceName;

    /**
     * 主要演员/演出团体
     */
    private String performer;

    /**
     * 演出类型(演唱会/话剧/音乐会等)
     */
    private String performanceType;

    /**
     * 演出日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate performanceDate;

    /**
     * 演出城市
     */
    private String city;

    /**
     * 演出地点
     */
    private String venue;

    /**
     * 票价
     */
    private BigDecimal ticketPrice;

    /**
     * 座位信息
     */
    private String seatInfo;

    /**
     * 演出时长(分钟)
     */
    private Integer duration;

    /**
     * 演出评分(1-5)
     */
    private Integer rating;

    /**
     * 演出评价
     */
    private String review;

    /**
     * 购票平台
     */
    private String purchasePlatform;

    /**
     * 购票订单号
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
