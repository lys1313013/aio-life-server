package top.aiolife.bankcard.pojo.req;
import jakarta.validation.constraints.*;
public record BankCardCoverEnabledReq(@NotNull @Min(0) @Max(1) Integer isEnabled) {}
