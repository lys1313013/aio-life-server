package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** IncomeCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncomeCreateReq {

    /**
     * 收入金额，未减税额
     */
    private BigDecimal grossAmount;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incDate;

    private String remark;

    /**
     * 收入类型ID
     */
    private Long incTypeId;

    /**
     * 收入税额；NULL表示未记录，0表示零税额
     */
    private BigDecimal taxAmount;
}
