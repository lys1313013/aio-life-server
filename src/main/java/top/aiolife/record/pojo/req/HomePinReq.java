package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 目标、纪念日的首页固定状态。 */
@Data
public class HomePinReq {
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isPinned;
}
