package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 设备与支出的关联；每笔支出保留一条可重新启用的关联记录。 */
@Getter
@Setter
@TableName("device_expense")
public class DeviceExpenseEntity extends BaseEntity {
    private Long userId;
    private Long deviceId;
    private Long expenseId;
}
