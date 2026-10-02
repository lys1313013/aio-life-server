package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** IncomeVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncomeVO {

    private Long id;

    private BigDecimal amt;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incDate;

    private String remark;

    /**
     * 收入类型ID
     */
    private Long incTypeId;

    private BigDecimal tax;

    /** 页面显示的记录时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
