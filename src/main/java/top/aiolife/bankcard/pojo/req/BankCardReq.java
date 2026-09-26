package top.aiolife.bankcard.pojo.req;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 全量编辑请求。编辑时 cardNo 留空保留原卡号，coverFileIds 为空清除卡面。 */
@Getter
@Setter
public class BankCardReq {
    private Long bankId;
    @Size(max = 100, message = "银行名称最多100个字符") private String customBankName;
    @Size(max = 100) private String cardName;
    @Size(max = 50) private String alias;
    @NotNull @Pattern(regexp = "debit|credit", message = "卡片类型不正确") private String cardType;
    @Size(max = 64) private String cardNo;
    @Size(max = 200) private String branchName;
    @NotNull @Pattern(regexp = "normal|frozen|lost|closed", message = "卡片状态不正确") private String status;
    private LocalDate openedDate;
    private LocalDate expiryMonth;
    @DecimalMin(value = "0", message = "信用额度不能为负数")
    @Digits(integer = 16, fraction = 2, message = "信用额度最多保留两位小数") private BigDecimal creditLimit;
    @Min(1) @Max(31) private Integer statementDay;
    @Min(1) @Max(31) private Integer repaymentDay;
    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "请选择有效颜色") private String coverColor;
    @Size(max = 1000) private String coverSourceUrl;
    @Min(0) private Integer sortOrder = 0;
    @Size(max = 1000) private String remark;
    @NotNull @Size(max = 20) private List<@NotNull Long> tagIds = List.of();
    @NotNull @Size(max = 1) private List<@NotNull @Pattern(regexp = "[a-fA-F0-9]{32}") String> coverFileIds = List.of();
}
