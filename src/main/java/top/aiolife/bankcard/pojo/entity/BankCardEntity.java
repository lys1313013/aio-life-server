package top.aiolife.bankcard.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 银行卡私有存储对象，不直接作为接口响应。 */
@Getter
@Setter
@TableName("bank_card")
public class BankCardEntity extends BaseEntity {
    private Long userId;
    // 完整编辑请求中的 null 表示清空；不能使用默认的 NOT_NULL 更新策略。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bankId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customBankName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cardName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String alias;
    private String cardType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cardNoCiphertext;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private byte[] cardNoFingerprint;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cardNoLast4;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String branchName;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate openedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expiryMonth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal creditLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer statementDay;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer repaymentDay;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long coverTemplateId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverColor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverSourceUrl;
    private Integer sortOrder;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
