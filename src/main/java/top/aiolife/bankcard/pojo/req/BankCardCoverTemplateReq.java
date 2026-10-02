package top.aiolife.bankcard.pojo.req;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 管理员保存卡面，不接受归属或审计字段。 */
@Getter
@Setter
public class BankCardCoverTemplateReq {
    @NotBlank @Size(max=100) private String name;
    @NotNull private Long bankId;
    @NotNull @Pattern(regexp="debit|credit") private String cardType;
    @Size(max=1000) private String sourceUrl;
    @NotNull @Min(0) @Max(1) private Integer isEnabled = 1;
    @NotNull @Min(0) private Integer sortOrder = 0;
    @NotNull @Pattern(regexp="[a-fA-F0-9]{32}") private String fileId;
}
