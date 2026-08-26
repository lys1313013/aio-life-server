package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
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
