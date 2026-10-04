package top.aiolife.bankcard.pojo.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 相对于目标卡片移动，分页和筛选不改变全局排序范围。 */
public record BankCardMoveReq(
        @NotNull @Positive Long id,
        @NotNull @Positive Long targetId,
        @NotNull Boolean after) {}
