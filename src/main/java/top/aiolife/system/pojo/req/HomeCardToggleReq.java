package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record HomeCardToggleReq(@NotNull @Min(0) @Max(1) Integer isEnabled) {}
