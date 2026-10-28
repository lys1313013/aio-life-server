package top.aiolife.bankcard.pojo.req;
import jakarta.validation.constraints.*;
public record BankCardTagReq(
        @NotBlank(message = "请输入标签名称") @Size(max = 20) String name,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
        @NotNull @Min(0) @Max(1) Integer isEnabled) {}
