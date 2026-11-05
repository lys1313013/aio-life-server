package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** 单批最多刷新十个当前用户目标，执行逐项返回错误，不覆盖成功快照。 */
@Data
public class GoalProgressRefreshReq {
    @NotEmpty
    @Size(max = 10)
    private List<@NotNull Long> ids;
}
