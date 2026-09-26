package top.aiolife.bankcard.pojo.entity;

import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 银行卡私有存储对象，不直接作为接口响应。 */
@Getter
@Setter
public class BankCardEntity extends BaseEntity {
    private Long userId;
    private Long bankId;
    private String customBankName;
    private String cardName;
    private String alias;
    private String cardType;
    private String cardNoCiphertext;
    private byte[] cardNoFingerprint;
    private String cardNoLast4;
    private String branchName;
    private String status;
    private LocalDate openedDate;
    private LocalDate expiryMonth;
    private BigDecimal creditLimit;
    private Integer statementDay;
    private Integer repaymentDay;
    private String coverColor;
    private String coverSourceUrl;
    private Integer sortOrder;
    private String remark;
}
