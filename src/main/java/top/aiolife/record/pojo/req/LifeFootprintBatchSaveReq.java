package top.aiolife.record.pojo.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 日期相同的明细一次提交；只删除明确指定的 ID，不按日期推断删除范围。 */
@Getter
@Setter
public class LifeFootprintBatchSaveReq {
    @NotNull @Size(max = 200)
    private List<@NotNull @Valid LifeFootprintBatchItemReq> items;
    @NotNull @Size(max = 200)
    private List<@NotNull @Min(1) Long> deletedIds = List.of();
}
