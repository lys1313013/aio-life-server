package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@ToString
@TableName("notification_channel_config")
public class NotificationChannelConfigEntity extends BaseEntity {
    private Long userId;
    private String channel;
    private Integer isEnabled;
    private String appId;
    private String appSecretCiphertext;
    private String receiverOpenId;
    private String receiverName;
}
