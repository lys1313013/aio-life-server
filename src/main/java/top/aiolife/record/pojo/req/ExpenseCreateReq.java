package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** ExpenseCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpenseCreateReq {

    /** 新增时关联的已有设备，与 newDevice 二选一；均为空时不关联。 */
    private Long deviceId;

    /** 随支出一起创建并关联的设备。 */
    @Valid
    private DeviceCreateReq newDevice;

    /**
     * 支付平台原始交易金额
     */
    private BigDecimal transactionAmount;

    /**
     * 记账金额，计入支出统计
     */
    private BigDecimal bookkeepingAmount;

    private Long expenseTypeId;

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
    private LocalDateTime expenseTime;

    /**
     * 交易号
     */
    private String transactionId;

    /**
     * 交易描述
     */
    private String expenseDesc;

    /**
     * 商家订单号
     */
    private String merchantOrderNo;

    /**
     * 交易状态
     */
    private String transactionStatus;
}
