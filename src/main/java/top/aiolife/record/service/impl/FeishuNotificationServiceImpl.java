package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.record.mapper.NotificationChannelConfigMapper;
import top.aiolife.record.mapper.NotificationDeliveryMapper;
import top.aiolife.record.mapper.NotificationPreferenceMapper;
import top.aiolife.record.notification.FeishuAppClient;
import top.aiolife.record.notification.NotificationBizType;
import top.aiolife.record.notification.NotificationChannel;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.record.notification.NotificationRequest;
import top.aiolife.record.pojo.entity.NotificationChannelConfigEntity;
import top.aiolife.record.pojo.entity.NotificationDeliveryEntity;
import top.aiolife.record.pojo.entity.NotificationPreferenceEntity;
import top.aiolife.record.pojo.req.FeishuChannelSaveReq;
import top.aiolife.record.pojo.req.NotificationPreferenceUpdateReq;
import top.aiolife.record.pojo.vo.FeishuChannelConfigVO;
import top.aiolife.record.pojo.vo.FeishuRecipientListVO;
import top.aiolife.record.pojo.vo.FeishuRecipientVO;
import top.aiolife.record.pojo.vo.NotificationPreferenceVO;
import top.aiolife.record.service.FeishuNotificationService;
import top.aiolife.record.service.IUserBindService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuNotificationServiceImpl implements FeishuNotificationService {

    private static final String CHANNEL = "FEISHU";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RETRY = "RETRY";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_RETRIES = 4;
    private static final long[] RETRY_MINUTES = {1, 5, 30, 120};

    private final NotificationChannelConfigMapper configMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final NotificationDeliveryMapper deliveryMapper;
    private final NotificationCryptoService cryptoService;
    private final FeishuAppClient feishuAppClient;
    private final ObjectMapper objectMapper;
    private final IUserBindService userBindService;

    @Override
    public FeishuChannelConfigVO getConfig(long userId) {
        NotificationChannelConfigEntity entity = findConfig(userId);
        boolean configured = entity != null
                && StringUtils.hasText(entity.getAppId())
                && StringUtils.hasText(entity.getAppSecretCiphertext());
        return FeishuChannelConfigVO.builder()
                .configured(configured)
                .enabled(configured && Integer.valueOf(1).equals(entity.getEnabled()))
                .bound(entity != null && StringUtils.hasText(entity.getReceiverOpenId()))
                .appId(entity == null ? null : entity.getAppId())
                .receiverOpenId(entity == null ? null : entity.getReceiverOpenId())
                .receiverName(entity == null ? null : entity.getReceiverName())
                .openIdMasked(entity == null ? null : maskOpenId(entity.getReceiverOpenId()))
                .build();
    }

    @Override
    public FeishuChannelConfigVO saveConfig(long userId, FeishuChannelSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        NotificationChannelConfigEntity entity = findConfig(userId);
        boolean creating = entity == null;
        String appId = StringUtils.hasText(req.getAppId())
                ? req.getAppId().trim()
                : creating ? null : entity.getAppId();
        String appSecret = StringUtils.hasText(req.getAppSecret())
                ? req.getAppSecret().trim()
                : creating ? null : cryptoService.decrypt(entity.getAppSecretCiphertext());
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new IllegalArgumentException("首次配置必须填写飞书 App ID 和 App Secret");
        }
        feishuAppClient.verifyCredentials(appId, appSecret);

        if (creating) {
            entity = new NotificationChannelConfigEntity();
            entity.setUserId(userId);
            entity.setChannel(CHANNEL);
            entity.fillCreateCommonField(userId);
        } else {
            entity.fillUpdateCommonField(userId);
        }

        boolean credentialsChanged = !creating && (
                !appId.equals(entity.getAppId()) || StringUtils.hasText(req.getAppSecret()));
        if (credentialsChanged) {
            clearBinding(entity);
        }
        entity.setAppId(appId);
        entity.setAppSecretCiphertext(cryptoService.encrypt(appSecret));

        List<FeishuAppClient.Recipient> recipients = null;
        if (creating || credentialsChanged || StringUtils.hasText(req.getOpenId())) {
            try {
                recipients = feishuAppClient.listVisibleRecipients(appId, appSecret);
            } catch (Exception e) {
                log.info("飞书应用凭证有效，但未能读取可选接收人，userId={}, reason={}", userId, e.getMessage());
            }
        }

        boolean bindingChanged = false;
        if (StringUtils.hasText(req.getOpenId())) {
            String openId = req.getOpenId().trim();
            feishuAppClient.validateOpenId(openId);
            FeishuAppClient.Recipient selected = recipients == null
                    ? null
                    : recipients.stream()
                    .filter(item -> openId.equals(item.openId()))
                    .findFirst()
                    .orElse(null);
            if (recipients != null && !recipients.isEmpty() && selected == null) {
                throw new IllegalArgumentException("所选用户不在当前飞书应用的通讯录范围内");
            }
            bindingChanged = !openId.equals(entity.getReceiverOpenId());
            bindRecipient(entity, openId, selected == null ? null : selected.name());
        } else if ((creating || credentialsChanged) && recipients != null && recipients.size() == 1) {
            FeishuAppClient.Recipient only = recipients.getFirst();
            bindRecipient(entity, only.openId(), only.name());
            bindingChanged = true;
        }

        if (bindingChanged) {
            entity.setEnabled(1);
        } else if (creating || credentialsChanged) {
            // 新应用存在多个/零个可见用户时先保存凭证，待用户选择接收人后再启用。
            entity.setEnabled(0);
        } else {
            if (Boolean.TRUE.equals(req.getEnabled()) && !StringUtils.hasText(entity.getReceiverOpenId())) {
                throw new IllegalArgumentException("请选择飞书通知接收人");
            }
            entity.setEnabled(Boolean.TRUE.equals(req.getEnabled()) ? 1 : 0);
        }
        if (creating) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        return getConfig(userId);
    }

    @Override
    public FeishuRecipientListVO listRecipients(long userId) {
        NotificationChannelConfigEntity config = requireConfig(userId, false, false);
        try {
            String appSecret = cryptoService.decrypt(config.getAppSecretCiphertext());
            List<FeishuRecipientVO> items = feishuAppClient
                    .listVisibleRecipients(config.getAppId(), appSecret)
                    .stream()
                    .map(item -> FeishuRecipientVO.builder()
                            .openId(item.openId())
                            .name(item.name())
                            .build())
                    .toList();
            long unnamedCount = items.stream()
                    .filter(item -> !StringUtils.hasText(item.getName()))
                    .count();
            String warning = null;
            if (items.isEmpty()) {
                warning = "应用通讯录范围没有直接授权的用户，可在高级设置中手动填写 open_id";
            } else if (unnamedCount > 0) {
                warning = "部分用户缺少昵称。请在飞书应用中开通“获取用户基本信息”权限并发布新版本";
            }
            return FeishuRecipientListVO.builder().items(items).warning(warning).build();
        } catch (Exception e) {
            return FeishuRecipientListVO.builder()
                    .items(List.of())
                    .warning("无法读取应用可见用户，请开通通讯录基本信息权限，或手动填写 open_id")
                    .build();
        }
    }

    @Override
    public void deleteConfig(long userId) {
        // 密钥配置物理删除，同时避免逻辑删除记录占用唯一键导致无法重新绑定。
        configMapper.hardDeleteByUserIdAndChannel(userId, CHANNEL);
    }

    @Override
    public void sendTest(long userId) {
        NotificationChannelConfigEntity config = requireConfig(userId, true, false);
        NotificationRequest request = new NotificationRequest(
                userId,
                "TEST",
                "AIO Life 飞书通知测试",
                "配置成功，你将在这里收到 AIO Life 的通知。",
                null,
                "test:" + userId + ":" + System.currentTimeMillis()
        );
        FeishuAppClient.SendResult result = send(config, request);
        if (!result.success()) {
            throw new IllegalStateException(result.errorMessage());
        }
    }

    @Override
    public List<NotificationPreferenceVO> listPreferences(long userId) {
        boolean hasLeetCode = hasLeetCodeBinding(userId);
        Map<String, NotificationPreferenceEntity> saved = preferenceMapper.selectList(
                        new LambdaQueryWrapper<NotificationPreferenceEntity>()
                                .eq(NotificationPreferenceEntity::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getBizType() + ":" + e.getChannel(),
                        Function.identity()));
        return Arrays.stream(NotificationBizType.values())
                .map(type -> {
                    boolean visible = !type.isRequiresLeetCode() || hasLeetCode;
                    List<NotificationPreferenceVO.ChannelState> channels = NotificationChannel.ALL.stream()
                            .map(ch -> {
                                String key = type.name() + ":" + ch;
                                boolean enabled = !saved.containsKey(key)
                                        || Integer.valueOf(1).equals(saved.get(key).getEnabled());
                                return NotificationPreferenceVO.ChannelState.builder()
                                        .channel(ch)
                                        .enabled(enabled)
                                        .build();
                            })
                            .toList();
                    return NotificationPreferenceVO.builder()
                            .bizType(type.name())
                            .description(type.getDescription())
                            .visible(visible)
                            .channels(channels)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NotificationPreferenceVO> updatePreferences(long userId, NotificationPreferenceUpdateReq req) {
        if (req == null || req.getItems() == null) {
            throw new IllegalArgumentException("通知偏好不能为空");
        }
        for (NotificationPreferenceUpdateReq.Item item : req.getItems()) {
            NotificationBizType.fromName(item.getBizType());
            if (item.getChannel() == null || !NotificationChannel.ALL.contains(item.getChannel())) {
                throw new IllegalArgumentException("不支持的通知渠道：" + item.getChannel());
            }
            NotificationPreferenceEntity existing = findPreference(userId, item.getBizType(), item.getChannel());
            if (existing == null) {
                NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
                entity.setUserId(userId);
                entity.setBizType(item.getBizType());
                entity.setChannel(item.getChannel());
                entity.setEnabled(Boolean.TRUE.equals(item.getEnabled()) ? 1 : 0);
                entity.fillCreateCommonField(userId);
                preferenceMapper.insert(entity);
            } else {
                existing.setEnabled(Boolean.TRUE.equals(item.getEnabled()) ? 1 : 0);
                existing.fillUpdateCommonField(userId);
                preferenceMapper.updateById(existing);
            }
        }
        return listPreferences(userId);
    }

    @Override
    public void sendIfEnabled(NotificationRequest request) {
        try {
            doSendIfEnabled(request);
        } catch (Exception e) {
            Long userId = request == null ? null : request.receiverUserId();
            String bizType = request == null ? null : request.bizType();
            log.error("飞书通知处理失败，不影响业务主流程，userId={}, bizType={}", userId, bizType, e);
        }
    }

    @Override
    public boolean isChannelEnabled(long userId, String bizType, String channel) {
        NotificationBizType.fromName(bizType);
        if (NotificationChannel.FEISHU.equals(channel)) {
            NotificationChannelConfigEntity config = findConfig(userId);
            if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) {
                return false;
            }
        }
        NotificationPreferenceEntity preference = findPreference(userId, bizType, channel);
        return preference == null || Integer.valueOf(1).equals(preference.getEnabled());
    }

    private void doSendIfEnabled(NotificationRequest request) {
        validateNotification(request);
        NotificationChannelConfigEntity config = findConfig(request.receiverUserId());
        if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) {
            return;
        }
        NotificationPreferenceEntity preference = findPreference(request.receiverUserId(), request.bizType(), CHANNEL);
        if (preference != null && !Integer.valueOf(1).equals(preference.getEnabled())) {
            return;
        }

        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.setDedupKey(request.dedupKey());
        delivery.setUserId(request.receiverUserId());
        delivery.setBizType(request.bizType());
        delivery.setChannel(CHANNEL);
        delivery.setStatus(STATUS_PENDING);
        delivery.setPayloadCiphertext(encryptPayload(request));
        delivery.setRetryCount(0);
        delivery.fillCreateCommonField(request.receiverUserId());
        try {
            deliveryMapper.insert(delivery);
        } catch (DuplicateKeyException e) {
            log.debug("飞书通知已投递，跳过重复请求，userId={}, bizType={}",
                    request.receiverUserId(), request.bizType());
            return;
        }
    }

    @Override
    public void retryPending() {
        List<NotificationDeliveryEntity> deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<NotificationDeliveryEntity>()
                        .eq(NotificationDeliveryEntity::getChannel, CHANNEL)
                        .and(wrapper -> wrapper
                                .eq(NotificationDeliveryEntity::getStatus, STATUS_PENDING)
                                .or(retry -> retry
                                        .eq(NotificationDeliveryEntity::getStatus, STATUS_RETRY)
                                        .le(NotificationDeliveryEntity::getNextRetryTime, LocalDateTime.now())))
                        .orderByAsc(NotificationDeliveryEntity::getCreateTime)
                        .last("LIMIT 50"));
        for (NotificationDeliveryEntity delivery : deliveries) {
            try {
                NotificationChannelConfigEntity config = findConfig(delivery.getUserId());
                if (config == null || !Integer.valueOf(1).equals(config.getEnabled())) {
                    markFailed(delivery, "CHANNEL_DISABLED", "飞书渠道已关闭");
                    continue;
                }
                NotificationRequest request = objectMapper.readValue(
                        cryptoService.decrypt(delivery.getPayloadCiphertext()), NotificationRequest.class);
                attempt(delivery, config, request);
            } catch (Exception e) {
                markFailed(delivery, "PAYLOAD_ERROR", "通知重试载荷解析失败");
                log.error("飞书通知重试失败，deliveryId={}", delivery.getId(), e);
            }
        }
    }

    private void attempt(NotificationDeliveryEntity delivery, NotificationChannelConfigEntity config,
                         NotificationRequest request) {
        FeishuAppClient.SendResult result = send(config, request);
        if (result.success()) {
            updateDelivery(delivery, STATUS_SUCCESS, result.providerCode(), null, null);
            log.info("飞书通知发送成功，deliveryId={}, userId={}, bizType={}",
                    delivery.getId(), delivery.getUserId(), delivery.getBizType());
            return;
        }
        int retryCount = delivery.getRetryCount() + 1;
        delivery.setRetryCount(retryCount);
        if (result.retryable() && retryCount <= MAX_RETRIES) {
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(RETRY_MINUTES[retryCount - 1]);
            updateDelivery(delivery, STATUS_RETRY, result.providerCode(), result.errorMessage(), nextRetry);
        } else {
            updateDelivery(delivery, STATUS_FAILED, result.providerCode(), result.errorMessage(), null);
        }
        log.warn("飞书通知发送失败，deliveryId={}, userId={}, bizType={}, code={}, retryable={}",
                delivery.getId(), delivery.getUserId(), delivery.getBizType(), result.providerCode(), result.retryable());
    }

    private FeishuAppClient.SendResult send(NotificationChannelConfigEntity config, NotificationRequest request) {
        String appSecret = cryptoService.decrypt(config.getAppSecretCiphertext());
        return feishuAppClient.send(config.getAppId(), appSecret, config.getReceiverOpenId(), request);
    }

    private void updateDelivery(NotificationDeliveryEntity delivery, String status, String providerCode,
                                String errorMessage, LocalDateTime nextRetryTime) {
        delivery.setStatus(status);
        delivery.setProviderCode(providerCode);
        delivery.setErrorMessage(errorMessage);
        delivery.setNextRetryTime(nextRetryTime);
        delivery.fillUpdateCommonField(delivery.getUserId());
        deliveryMapper.updateById(delivery);
    }

    private void markFailed(NotificationDeliveryEntity delivery, String providerCode, String errorMessage) {
        updateDelivery(delivery, STATUS_FAILED, providerCode, errorMessage, null);
    }

    private NotificationChannelConfigEntity requireConfig(
            long userId, boolean requireBinding, boolean requireEnabled) {
        NotificationChannelConfigEntity config = findConfig(userId);
        if (config == null) {
            throw new IllegalArgumentException("请先配置飞书应用");
        }
        if (requireBinding && !StringUtils.hasText(config.getReceiverOpenId())) {
            throw new IllegalArgumentException("请先选择飞书通知接收人");
        }
        if (requireEnabled && !Integer.valueOf(1).equals(config.getEnabled())) {
            throw new IllegalArgumentException("请先启用飞书通知");
        }
        return config;
    }

    private NotificationChannelConfigEntity findConfig(long userId) {
        return configMapper.selectOne(new LambdaQueryWrapper<NotificationChannelConfigEntity>()
                .eq(NotificationChannelConfigEntity::getUserId, userId)
                .eq(NotificationChannelConfigEntity::getChannel, CHANNEL));
    }

    private NotificationPreferenceEntity findPreference(long userId, String bizType, String channel) {
        return preferenceMapper.selectOne(new LambdaQueryWrapper<NotificationPreferenceEntity>()
                .eq(NotificationPreferenceEntity::getUserId, userId)
                .eq(NotificationPreferenceEntity::getBizType, bizType)
                .eq(NotificationPreferenceEntity::getChannel, channel));
    }

    private String encryptPayload(NotificationRequest request) {
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            throw new IllegalStateException("通知载荷加密失败", e);
        }
    }

    private void validateNotification(NotificationRequest request) {
        if (request == null || request.receiverUserId() == null) {
            throw new IllegalArgumentException("通知接收用户不能为空");
        }
        NotificationBizType.fromName(request.bizType());
        if (!StringUtils.hasText(request.title()) || !StringUtils.hasText(request.textContent())) {
            throw new IllegalArgumentException("通知标题和内容不能为空");
        }
        if (!StringUtils.hasText(request.dedupKey()) || request.dedupKey().length() > 128) {
            throw new IllegalArgumentException("通知去重键不能为空且长度不能超过 128");
        }
    }

    private String maskOpenId(String openId) {
        if (!StringUtils.hasText(openId)) {
            return null;
        }
        if (openId.length() <= 10) {
            return "ou_…";
        }
        return openId.substring(0, 6) + "…" + openId.substring(openId.length() - 4);
    }

    private void clearBinding(NotificationChannelConfigEntity entity) {
        entity.setReceiverOpenId(null);
        entity.setReceiverName(null);
        entity.setEnabled(0);
    }

    private void bindRecipient(NotificationChannelConfigEntity entity, String openId, String name) {
        entity.setReceiverOpenId(openId);
        entity.setReceiverName(StringUtils.hasText(name) ? name : null);
    }

    private boolean hasLeetCodeBinding(long userId) {
        var bind = userBindService.getBindByUserIdAndPlatform(userId, "leetcode");
        return bind != null && StringUtils.hasText(bind.getPlatformUsername());
    }
}
