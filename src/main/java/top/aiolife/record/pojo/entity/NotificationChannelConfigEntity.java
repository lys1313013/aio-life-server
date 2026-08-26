package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_channel_config")
public class NotificationChannelConfigEntity extends BaseEntity {
    private Long userId;
    private String channel;
    private Integer enabled;
    private String appId;
    private String appSecretCiphertext;
    private String receiverOpenId;
    private String receiverName;
}
