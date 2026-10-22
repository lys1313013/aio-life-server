package top.aiolife.record.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import top.aiolife.record.pojo.req.DeviceCreateReq;

@Data
@AllArgsConstructor
public class ExpenseDeviceContextVO {
    private DeviceVO device;
    private DeviceCreateReq defaults;
}
