package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.NotNull;

public record HomeCardToggleReq(@NotNull Boolean enabled) {}
