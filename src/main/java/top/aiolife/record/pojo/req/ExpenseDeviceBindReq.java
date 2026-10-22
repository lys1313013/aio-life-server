package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExpenseDeviceBindReq {
    @NotNull
    private Long deviceId;
}
