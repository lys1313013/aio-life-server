package top.aiolife.record.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.mapper.NotificationDeliveryMapper;
import top.aiolife.record.mapper.NotificationPreferenceMapper;
import top.aiolife.record.pojo.entity.NotificationDeliveryEntity;
import top.aiolife.record.pojo.entity.NotificationPreferenceEntity;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.wechat.WechatMiniClient;
import top.aiolife.sso.wechat.WechatMiniProperties;
import top.aiolife.system.service.WechatSystemConfigService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一次性订阅的真实额度由微信判断，客户端上报和业务偏好都不作为授权凭证。 */
@Service
@Slf4j
public class WechatSubscriptionService {
    public static final String BIZ_TYPE = "LEETCODE_REMINDER";
    private static final String CHANNEL = NotificationChannel.WECHAT_MINI;
    private static final long[] RETRY_MINUTES = {1, 5, 30, 120};
    private final WechatSubscriptionProperties properties;
    private final WechatMiniProperties miniProperties;
    private final WechatMiniClient client;
    private final UserMapper users;
    private final NotificationPreferenceMapper preferences;
    private final NotificationDeliveryMapper deliveries;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;
    private final TransactionTemplate transaction;
    @Autowired(required = false)
    private WechatSystemConfigService configuration;

    private WechatSystemConfigService.Snapshot settings() {
        return configuration == null ? new WechatSystemConfigService.Snapshot(miniProperties, properties) : configuration.snapshot();
    }

    public WechatSubscriptionService(WechatSubscriptionProperties properties, WechatMiniProperties miniProperties,
            WechatMiniClient client, UserMapper users, NotificationPreferenceMapper preferences,
            NotificationDeliveryMapper deliveries, NotificationCryptoService crypto, ObjectMapper json,
            DistributedLockExecutor locks, DataSourceTransactionManager transactionManager) {
        this.properties = properties;
        this.miniProperties = miniProperties;
        this.client = client;
        this.users = users;
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.crypto = crypto;
        this.json = json;
        this.locks = locks;
        // 入队提交、发送前占位和结果保存分开提交，发送后的数据库异常不能触发再次发送。
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Config getConfig(long userId) {
        WechatSubscriptionProperties properties = settings().subscription();
        boolean enabled = properties.configured() && client.enabled();
        return new Config(enabled, isBound(users.selectById(userId)), BIZ_TYPE,
                enabled ? properties.getTemplateId() : null);
    }

    public record Config(boolean enabled, boolean bound, String bizType, String templateId) {}
    public record Payload(String appId, String openid, String templateId, String environment,
                          Map<String, Map<String, String>> data, boolean test) {}

    public void sendIfEnabled(NotificationRequest request) {
        if (request == null || !BIZ_TYPE.equals(request.bizType())) return;
        try {
            enqueue(request, false);
        } catch (Exception e) {
            // 不记录含正文、openid、Token 或密钥的异常消息。
            log.warn("微信通知入队失败 userId={} type={}", request.receiverUserId(), e.getClass().getSimpleName());
        }
    }

    public void sendTest(long userId) {
        NotificationRequest request = new NotificationRequest(userId, BIZ_TYPE, "力扣未打卡提醒",
                "这是一条测试提醒", null, "wechat:test:" + UUID.randomUUID());
        NotificationDeliveryEntity delivery = enqueue(request, true);
        if (delivery == null) throw new IllegalStateException("微信通知未启用或未绑定，请使用微信登录绑定账号");
        process(delivery.getId());
        NotificationDeliveryEntity result = deliveries.selectById(delivery.getId());
        if (result != null && "SUCCESS".equals(result.getStatus())) return;
        if (result != null && "43101".equals(result.getProviderCode())) {
            throw new IllegalStateException("没有可用的订阅机会，请先在小程序点击订阅一次");
        }
        if (result != null && "UNKNOWN".equals(result.getStatus())) {
            throw new IllegalStateException("发送结果未确认，请先检查微信通知；系统不会自动重发");
        }
        throw new IllegalStateException("测试发送失败，请检查微信模板配置和账号绑定后重试");
    }

    private NotificationDeliveryEntity enqueue(NotificationRequest request, boolean test) {
        if (request.receiverUserId() == null || !StringUtils.hasText(request.dedupKey())
                || request.dedupKey().length() > 128) throw new IllegalArgumentException("通知事件无效");
        NotificationDeliveryEntity[] result = new NotificationDeliveryEntity[1];
        locks.tryRun("notification:wechat-mini:enqueue:" + request.receiverUserId() + ":" + request.dedupKey(), () ->
                transaction.executeWithoutResult(status -> {
                    var settings = settings();
                    WechatSubscriptionProperties properties = settings.subscription();
                    WechatMiniProperties miniProperties = settings.mini();
                    UserEntity user = users.selectById(request.receiverUserId());
                    if (!properties.configured() || !client.enabled() || !isBound(user)
                            || (!test && !enabled(request.receiverUserId()))) return;
                    long existing = deliveries.selectCount(new LambdaQueryWrapper<NotificationDeliveryEntity>()
                            .eq(NotificationDeliveryEntity::getUserId, request.receiverUserId())
                            .eq(NotificationDeliveryEntity::getChannel, CHANNEL)
                            .eq(NotificationDeliveryEntity::getDedupKey, request.dedupKey()));
                    if (existing > 0) return;
                    Payload payload = new Payload(miniProperties.getAppId(), user.getWechatOpenid(),
                            properties.getTemplateId(), properties.getEnvironment(), data(request, properties), test);
                    NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
                    delivery.setUserId(request.receiverUserId());
                    delivery.setBizType(BIZ_TYPE);
                    delivery.setChannel(CHANNEL);
                    delivery.setDedupKey(request.dedupKey());
                    delivery.setStatus("PENDING");
                    delivery.setRetryCount(0);
                    try {
                        delivery.setPayloadCiphertext(crypto.encrypt(json.writeValueAsString(payload)));
                    } catch (Exception e) {
                        throw new IllegalStateException("微信通知载荷加密失败");
                    }
                    delivery.fillCreateCommonField(request.receiverUserId());
                    deliveries.insert(delivery);
                    result[0] = delivery;
                }));
        return result[0];
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void deliverPending() {
        List<NotificationDeliveryEntity> pending = deliveries.selectPage(new Page<NotificationDeliveryEntity>(1, 50, false),
                new LambdaQueryWrapper<NotificationDeliveryEntity>()
                .eq(NotificationDeliveryEntity::getChannel, CHANNEL)
                .and(q -> q.eq(NotificationDeliveryEntity::getStatus, "PENDING")
                        .or(r -> r.eq(NotificationDeliveryEntity::getStatus, "RETRY")
                                .le(NotificationDeliveryEntity::getNextRetryTime, LocalDateTime.now())))
                .orderByAsc(NotificationDeliveryEntity::getCreateTime)).getRecords();
        for (NotificationDeliveryEntity delivery : pending) {
            try {
                process(delivery.getId());
            } catch (Exception e) {
                log.warn("微信通知投递处理失败 deliveryId={} type={}", delivery.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private void process(long id) {
        locks.tryRun("notification:wechat-mini:delivery:" + id, () -> {
            NotificationDeliveryEntity delivery = deliveries.selectById(id);
            if (delivery == null || !CHANNEL.equals(delivery.getChannel())
                    || !("PENDING".equals(delivery.getStatus()) || "RETRY".equals(delivery.getStatus()))) return;
            if (delivery.getNextRetryTime() != null && delivery.getNextRetryTime().isAfter(LocalDateTime.now())) return;
            if (delivery.getCreateTime().toLocalDate().isBefore(LocalDateTime.now().toLocalDate())) {
                update(delivery, "FAILED", "EXPIRED", null);
                return;
            }
            Payload payload;
            try {
                payload = json.readValue(crypto.decrypt(delivery.getPayloadCiphertext()), Payload.class);
            } catch (Exception e) {
                update(delivery, "FAILED", "PAYLOAD_ERROR", null);
                return;
            }
            var settings = settings();
            WechatSubscriptionProperties properties = settings.subscription();
            WechatMiniProperties miniProperties = settings.mini();
            UserEntity user = users.selectById(delivery.getUserId());
            if (!properties.configured() || !client.enabled() || !isBound(user)
                    || !miniProperties.getAppId().equals(payload.appId())
                    || !user.getWechatOpenid().equals(payload.openid())
                    || !properties.getTemplateId().equals(payload.templateId())
                    || !properties.getEnvironment().equals(payload.environment())
                    || (!payload.test() && !enabled(delivery.getUserId()))) {
                update(delivery, "FAILED", "CHANNEL_CHANGED", null);
                return;
            }
            update(delivery, "SENDING", null, null);
            // 若进程在发送后退出，保留 SENDING 待人工核查；不可因超时自动重新投递。
            WechatMiniClient.SubscriptionResult result;
            try {
                result = client.sendSubscription(payload.openid(), payload.templateId(), payload.environment(), payload.data());
            } catch (Exception e) {
                // 未分类异常保守视为结果未知。
                result = new WechatMiniClient.SubscriptionResult("UNKNOWN", false, false);
            }
            if (result.success()) {
                update(delivery, "SUCCESS", result.code(), null);
            } else if (result.retryable() && !payload.test() && delivery.getRetryCount() < RETRY_MINUTES.length) {
                int retry = delivery.getRetryCount();
                delivery.setRetryCount(retry + 1);
                update(delivery, "RETRY", result.code(), LocalDateTime.now().plusMinutes(RETRY_MINUTES[retry]));
            } else {
                update(delivery, "UNKNOWN".equals(result.code()) ? "UNKNOWN" : "FAILED", result.code(), null);
            }
        });
    }

    private void update(NotificationDeliveryEntity delivery, String state, String code, LocalDateTime nextRetry) {
        transaction.executeWithoutResult(status -> {
            int updated = deliveries.update(null, new LambdaUpdateWrapper<NotificationDeliveryEntity>()
                .eq(NotificationDeliveryEntity::getId, delivery.getId())
                .set(NotificationDeliveryEntity::getStatus, state)
                .set(NotificationDeliveryEntity::getProviderCode, code)
                .set(NotificationDeliveryEntity::getRetryCount, delivery.getRetryCount())
                .set(NotificationDeliveryEntity::getNextRetryTime, nextRetry)
                .set(NotificationDeliveryEntity::getUpdateUser, delivery.getUserId())
                .set(NotificationDeliveryEntity::getUpdateTime, LocalDateTime.now()));
            if (updated != 1) throw new IllegalStateException("通知投递状态保存失败");
        });
        delivery.setStatus(state);
        delivery.setProviderCode(code);
        delivery.setNextRetryTime(nextRetry);
    }

    private boolean enabled(long userId) {
        NotificationPreferenceEntity preference = preferences.selectOne(new LambdaQueryWrapper<NotificationPreferenceEntity>()
                .eq(NotificationPreferenceEntity::getUserId, userId)
                .eq(NotificationPreferenceEntity::getBizType, BIZ_TYPE)
                .eq(NotificationPreferenceEntity::getChannel, CHANNEL));
        return preference != null && Integer.valueOf(1).equals(preference.getIsEnabled());
    }

    private boolean isBound(UserEntity user) {
        return user != null && !Integer.valueOf(1).equals(user.getIsDeleted()) && StringUtils.hasText(user.getWechatOpenid());
    }

    private Map<String, Map<String, String>> data(NotificationRequest request, WechatSubscriptionProperties properties) {
        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.getTitleField())) data.put(properties.getTitleField(), Map.of("value", thing(request.title())));
        if (StringUtils.hasText(properties.getContentField())) data.put(properties.getContentField(), Map.of("value", thing(request.textContent())));
        if (StringUtils.hasText(properties.getTimeField())) data.put(properties.getTimeField(),
                Map.of("value", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        return data;
    }

    private String thing(String value) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("通知内容不能为空");
        String text = value.replaceAll("\\s+", " ").trim();
        return text.substring(0, text.offsetByCodePoints(0, Math.min(20, text.codePointCount(0, text.length()))));
    }
}
