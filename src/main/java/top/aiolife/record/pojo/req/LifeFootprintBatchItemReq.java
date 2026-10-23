package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** 批量保存的单条明细；无 ID 表示新增，有 ID 表示更新本人已有记录。 */
@Getter
@Setter
public class LifeFootprintBatchItemReq extends LifeFootprintSaveReq {
    @Min(1)
    private Long id;
}
