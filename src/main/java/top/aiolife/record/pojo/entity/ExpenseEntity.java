package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/03 21:02
 */
@Getter
@Setter
@ToString
@TableName("expense")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpenseEntity extends IdentityEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 交易金额（支付软件上的支付金额）
     */
    private BigDecimal transactionAmt;
    /**
     * 记账金额
     */
    private BigDecimal amt;
    private Long expTypeId;

    private Long userId;

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

    @TableLogic
    private Integer isDeleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private Long createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    private Long updateUser;

    @Override
    protected Object identityKey() {
        return id;
    }
}
