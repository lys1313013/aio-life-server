package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 菜单展示端配置：1=Web，2=移动端，按位组合；0=均不展示。 */
@Data
public class MenuVisibilityUpdateReq {
    @NotNull @Min(0) @Max(3)
    private Integer visibleClients;
}
