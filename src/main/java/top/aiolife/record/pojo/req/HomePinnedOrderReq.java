package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 按显示顺序提交该业务的全部固定记录 ID。 */
@Data
public class HomePinnedOrderReq {
    @NotNull
    @Size(max = 10000)
    private List<@NotNull Long> ids;
}
