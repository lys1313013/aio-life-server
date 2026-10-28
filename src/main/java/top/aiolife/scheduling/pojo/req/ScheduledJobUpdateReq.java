package top.aiolife.scheduling.pojo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

/** 配置更新，revision 防止覆盖其他管理员刚保存的设置。 */
@Data
public class ScheduledJobUpdateReq {
    @NotBlank @Size(max = 100)
    private String cron;
    @NotNull
    @Min(0)
    @Max(1)
    private Integer isEnabled;
    @NotNull @PositiveOrZero
    private Integer revision;
}
