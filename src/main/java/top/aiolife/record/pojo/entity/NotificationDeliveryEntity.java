package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@ToString
@TableName("notification_delivery")
public class NotificationDeliveryEntity extends BaseEntity {
    private String dedupKey;
    private Long userId;
    private String bizType;
    private String channel;
    private String status;
    private String payloadCiphertext;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String providerCode;
    private String errorMessage;
}
