package top.aiolife.bankcard.pojo.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 只含脱敏信息；文件 ID 来自 file 反向关联。 */
@Getter
@Setter
public class BankCardVO {
    private String id;
    private String bankId;
    private String bankName;
    private String bankCode;
    private String customBankName;
    private String cardName;
    private String alias;
    private String cardType;
    private String cardNoFirst4;
    private String cardNoLast4;
    private String branchName;
    private String status;
    private LocalDate openedDate;
    private LocalDate expiryMonth;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private BigDecimal creditLimit;
    private Integer statementDay;
    private Integer repaymentDay;
    private String coverTemplateId;
    private String coverTemplateFileId;
    private String coverTemplatePublicUrl;
    private String coverTemplateName;
    private String coverColor;
    private String coverSourceUrl;
    private Integer sortOrder;
    private String remark;
    private List<Tag> tags = List.of();
    private List<String> coverFileIds = List.of();
    public record Tag(String id, String name, String color, String status) {}
    public record Bank(String id, String name, String code, boolean enabled) {}
}
