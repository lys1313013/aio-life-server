package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.record.pojo.entity.NotificationDeliveryEntity;

@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDeliveryEntity> {
    /** 冲突时保留已有投递，避免并发入队插入重复事件或让重复键异常中止外层事务。 */
    @Insert("""
        INSERT INTO notification_delivery (id, dedup_key, user_id, biz_type, channel, status, payload_ciphertext,
            retry_count, next_retry_time, provider_code, error_message,
            create_user, create_time, update_user, update_time, is_deleted)
        VALUES (#{id}, #{dedupKey}, #{userId}, #{bizType}, #{channel}, #{status}, #{payloadCiphertext},
            #{retryCount}, #{nextRetryTime}, #{providerCode}, #{errorMessage},
            #{createUser}, #{createTime}, #{updateUser}, #{updateTime}, #{isDeleted})
        ON CONFLICT DO NOTHING
        """)
    int insertIfAbsent(NotificationDeliveryEntity entity);
}
