package top.aiolife.bankcard.pojo.query;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import top.aiolife.core.query.PaginationRequest;
import lombok.Getter;
import lombok.Setter;

/** 公共卡面分页及筛选条件。 */
@Getter
@Setter
public class BankCardCoverQuery implements PaginationRequest {
    @NotNull @Min(1) private Integer page = 1;
    @NotNull @Min(1) @Max(100) @JsonAlias("size")
    @Schema(description = "每页条数，最多 100", defaultValue = "24")
    private Integer pageSize = 24;
    @Override
    public int maximumPageSize() { return 100; }
    @JsonIgnore @Schema(hidden = true)
    public Integer getSize() { return pageSize; }
    public void setSize(Integer size) { this.pageSize = size; }
    @Size(max=100) private String keyword;
    private Long bankId;
    @Pattern(regexp="debit|credit") private String cardType;
    @Min(0) @Max(1) private Integer isEnabled;
}
