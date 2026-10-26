package top.aiolife.bankcard.pojo.query;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import top.aiolife.core.query.PaginationRequest;
import lombok.Getter;
import lombok.Setter;

/** 公共卡面分页及筛选条件。 */
@Getter
@Setter
public class BankCardCoverQuery implements PaginationRequest {
    @NotNull @Min(1) private Integer page = 1;
    @NotNull @Min(1) @Max(100)
    @Schema(description = "每页条数，最多 100", defaultValue = "24")
    private Integer pageSize = 24;
    @Override
    public int maximumPageSize() { return 100; }
    @Size(max=100) private String keyword;
    private Long bankId;
    @Pattern(regexp="debit|credit") private String cardType;
    @Min(0) @Max(1) private Integer isEnabled;
}
