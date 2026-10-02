package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** ExpenseUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpenseUpdateReq {

    @NotNull
    private Long id;

    private BigDecimal transactionAmt;

    /**
     * 记账金额
     */
    private BigDecimal amt;

    private Long expTypeId;

    /**
     * 支付方式
     */
    private Long payTypeId;

    /**
     * 交易对方
     */
    private String counterparty;

    /**
     * 对方账号
     */
    private String counterpartyAcct;

    /**
     * 备注
     */
    private String remark;

    /**
     * 支出时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expTime;

    /**
     * 交易号
     */
    private String transactionId;

    /**
     * 交易描述
     */
    private String expDesc;

    /**
     * 商家订单号
     */
    private String merchantOrderNo;

    /**
     * 交易状态
     */
    private String transactionStatus;
}
