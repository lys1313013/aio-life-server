package top.aiolife.membership.pojo.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 平台管理完整保存请求。 */
@Data
public class MembershipProviderReq {
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,49}")
    private String code;
    @NotBlank @Pattern(regexp = "video|music|shopping|cloud|study|game|other")
    private String category;
    @Size(max = 64)
    private String iconKey;
    @NotNull @Min(0) @Max(999999)
    private Integer sortOrder = 0;
    @NotNull @Min(0) @Max(1)
    private Integer isEnabled = 1;
}
