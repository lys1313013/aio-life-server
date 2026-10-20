package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.record.notification.WechatSubscriptionProperties;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.wechat.WechatMiniProperties;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.vo.SystemConfigVO;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 复用系统配置表保存微信配置；环境变量只在尚未保存后台配置时兼容使用。 */
@Service
@RequiredArgsConstructor
public class WechatSystemConfigService {
    public static final String KEY = "wechat.mini";
    private final ISystemConfigMapper mapper;
    private final WechatMiniProperties defaults;
    private final WechatSubscriptionProperties subscriptionDefaults;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;
    private final RedisUtil redis;
    private final UserMapper users;

    public record Snapshot(WechatMiniProperties mini, WechatSubscriptionProperties subscription) {}

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        private boolean enabled;
        private String appId = "";
        private String appSecret;
        private String appSecretCiphertext;
        private boolean subscribeEnabled;
        private String templateId = "";
        private String titleField = "thing1";
        private String contentField = "thing2";
        private String timeField = "time3";
        private String environment = "formal";
        private boolean webScanEnabled;
        private String webScanEnvVersion = "release";
    }

    public Snapshot snapshot() {
        SystemConfigEntity row = load();
        if (row == null) return new Snapshot(defaults, subscriptionDefaults);
        Value value = read(row.getConfigValue());
        if (StringUtils.hasText(value.getAppSecret())) throw new IllegalStateException("微信配置包含未加密密钥，请通过后台重新保存");
        String secret = StringUtils.hasText(value.getAppSecretCiphertext()) ? crypto.decrypt(value.getAppSecretCiphertext()) : "";
        return snapshot(value, secret);
    }

    public SystemConfigVO getAdminConfig() {
        SystemConfigEntity row = load();
        Snapshot current = snapshot();
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("enabled", current.mini().isEnabled());
        form.put("appId", current.mini().getAppId());
        form.put("appSecretConfigured", StringUtils.hasText(current.mini().getAppSecret()));
        form.put("subscribeEnabled", current.subscription().isEnabled());
        form.put("templateId", current.subscription().getTemplateId());
        form.put("titleField", current.subscription().getTitleField());
        form.put("contentField", current.subscription().getContentField());
        form.put("timeField", current.subscription().getTimeField());
        form.put("environment", current.subscription().getEnvironment());
        form.put("webScanEnabled", current.mini().isWebScanEnabled());
        form.put("webScanEnvVersion", current.mini().getWebScanEnvVersion());
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(KEY);
        vo.setConfigType("JSON");
        vo.setDescription("微信小程序与订阅通知");
        vo.setConfigValue(write(form));
        if (row != null && row.getUpdateTime() != null) {
            vo.setUpdateTime(row.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfigVO save(String input, long operatorId) {
        boolean acquired = locks.tryRun("system-config:wechat-mini:save", () -> {
            Value value = read(input);
            // 密文只能由服务端生成；不能通过通用 KV 接口写入任意密文。
            if (value.getAppSecretCiphertext() != null) throw new IllegalArgumentException("不能直接提交微信密文");
            Snapshot previous = snapshot();
            value.setAppId(trim(value.getAppId()));
            value.setTemplateId(trim(value.getTemplateId()));
            value.setTitleField(trim(value.getTitleField()));
            value.setContentField(trim(value.getContentField()));
            value.setTimeField(trim(value.getTimeField()));
            String secret = StringUtils.hasText(value.getAppSecret()) ? value.getAppSecret().trim()
                    : value.getAppId().equals(previous.mini().getAppId()) ? previous.mini().getAppSecret() : "";
            if (!value.getAppId().isEmpty() && !value.getAppId().matches("wx[0-9a-fA-F]{16}")) {
                throw new IllegalArgumentException("请输入有效的小程序 AppID");
            }
            if ((value.isEnabled() || value.isSubscribeEnabled() || value.isWebScanEnabled())
                    && (!StringUtils.hasText(value.getAppId()) || !StringUtils.hasText(secret))) {
                throw new IllegalArgumentException("开启微信能力前请填写 AppID 和 AppSecret");
            }
            if ((value.isSubscribeEnabled() || value.isWebScanEnabled()) && !value.isEnabled()) {
                throw new IllegalArgumentException("请先启用微信登录");
            }
            if (secret.length() > 256) throw new IllegalArgumentException("AppSecret 长度无效");
            if (value.getEnvironment() == null || value.getWebScanEnvVersion() == null
                    || !Set.of("formal", "trial", "developer").contains(value.getEnvironment())
                    || !Set.of("release", "trial", "develop").contains(value.getWebScanEnvVersion())) {
                throw new IllegalArgumentException("微信版本配置无效");
            }
            Snapshot candidate = snapshot(value, secret);
            if (value.isSubscribeEnabled() && !candidate.subscription().configured()) {
                throw new IllegalArgumentException("请填写模板 ID，并检查字段类型、编号及字段名是否重复");
            }
            if (!value.getAppId().equals(previous.mini().getAppId()) && StringUtils.hasText(previous.mini().getAppId())
                    && users.selectCount(new LambdaQueryWrapper<UserEntity>().isNotNull(UserEntity::getWechatOpenid)
                            .ne(UserEntity::getWechatOpenid, "")) > 0) {
                throw new IllegalArgumentException("已有微信绑定账号，不能直接更换 AppID，请先安排账号迁移");
            }
            value.setAppSecret(null);
            value.setAppSecretCiphertext(StringUtils.hasText(secret) ? crypto.encrypt(secret) : null);
            SystemConfigEntity row = load();
            if (row == null) {
                row = new SystemConfigEntity();
                row.setConfigKey(KEY);
                row.setConfigType("JSON");
                row.setDescription("微信小程序与订阅通知");
                row.setConfigValue(write(value));
                row.fillCreateCommonField(operatorId);
                mapper.insert(row);
            } else {
                row.setConfigValue(write(value));
                row.fillUpdateCommonField(operatorId);
                mapper.updateById(row);
            }
            if (!secret.equals(previous.mini().getAppSecret()) || !value.getAppId().equals(previous.mini().getAppId())) {
                redis.delete("auth:wechat:token:" + previous.mini().getAppId());
                redis.delete("auth:wechat:token:" + value.getAppId());
            }
        });
        if (!acquired) throw new IllegalStateException("配置正在保存，请稍后重试");
        return getAdminConfig();
    }

    private Snapshot snapshot(Value value, String secret) {
        WechatMiniProperties mini = new WechatMiniProperties();
        mini.setEnabled(value.isEnabled());
        mini.setAppId(value.getAppId());
        mini.setAppSecret(secret);
        mini.setWebScanEnabled(value.isWebScanEnabled());
        mini.setWebScanEnvVersion(value.getWebScanEnvVersion());
        WechatSubscriptionProperties subscription = new WechatSubscriptionProperties();
        subscription.setEnabled(value.isSubscribeEnabled());
        subscription.setTemplateId(value.getTemplateId());
        subscription.setTitleField(value.getTitleField());
        subscription.setContentField(value.getContentField());
        subscription.setTimeField(value.getTimeField());
        subscription.setEnvironment(value.getEnvironment());
        return new Snapshot(mini, subscription);
    }

    private SystemConfigEntity load() {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>().eq(SystemConfigEntity::getConfigKey, KEY));
    }

    private Value read(String value) {
        try {
            Value parsed = json.readValue(value, Value.class);
            if (parsed == null) throw new IllegalArgumentException();
            return parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("微信配置必须是有效的配置对象");
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("微信配置保存失败"); }
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
}
