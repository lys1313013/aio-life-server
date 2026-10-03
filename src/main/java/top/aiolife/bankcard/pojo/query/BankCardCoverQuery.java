package top.aiolife.bankcard.pojo.query;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 公共卡面分页及筛选条件。 */
@Getter
@Setter
public class BankCardCoverQuery {
    @Min(1) private int page = 1;
    @Min(1) @Max(100) private int size = 24;
    @Size(max=100) private String keyword;
    private Long bankId;
    @Pattern(regexp="debit|credit") private String cardType;
    @Min(0) @Max(1) private Integer isEnabled;
}
