package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HomeCardOrderReq(@NotBlank String group,
                              @NotNull @Size(min = 1, max = 100) List<@NotBlank String> keys) {}
