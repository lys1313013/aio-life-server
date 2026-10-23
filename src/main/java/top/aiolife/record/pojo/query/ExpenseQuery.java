package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-29 21:32
 */
@Data
public class ExpenseQuery {

    /**
     * 分类id
     */
    private String expTypeId;

    /**
     * 支付方式id
     */
    private String payTypeId;

     /**
     * 年份
     */
    private String year;

    /**
     * 备注
     */
    private String remark;

     /**
     * 交易对方
     */
    private String counterparty;

    /**
     * 交易描述
     */
    private String expDesc;

     /**
     * 支出开始时间
     */
     @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

     /**
     * 支出结束时间
     */
     @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 日历明细范围的结束时间，不包含该时刻；旧 endTime 仍保持包含语义。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTimeBefore;

    /** 仅查询未填写支出时间的历史账目。 */
    private Boolean undated;
}
