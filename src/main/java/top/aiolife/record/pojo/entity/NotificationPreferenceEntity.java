package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@ToString
@TableName("notification_preference")
public class NotificationPreferenceEntity extends BaseEntity {
    private Long userId;
    private String bizType;
    private String channel;
    private Integer isEnabled;
}
