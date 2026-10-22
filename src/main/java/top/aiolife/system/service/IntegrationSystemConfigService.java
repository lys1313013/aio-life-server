package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.vo.SystemConfigVO;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 第三方接口配置实时读取；密钥仅由服务端加密，所有管理读取均脱敏。 */
@Service
@RequiredArgsConstructor
public class IntegrationSystemConfigService {
    public static final String TYPESAFE_KEY = "typesafe.recommendation";
    public static final String MBTI_KEY = "mbti.api";
    public static final List<String> KEYS = List.of(TYPESAFE_KEY, MBTI_KEY);
    private final ISystemConfigMapper mapper;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;
    private final IntegrationConfigProperties defaults;

    public record TypesafeSnapshot(boolean enabled, String apiKey, String model,
                                   int requestTimeoutMs, int dataCacheTtlMs) {}
    public record MbtiSnapshot(String apiKey, String baseUrl) {}

    public TypesafeSnapshot typesafe() {
        ObjectNode value = current(TYPESAFE_KEY, load(TYPESAFE_KEY));
        return new TypesafeSnapshot(value.path("enabled").asBoolean(), secret(value),
                value.path("model").asText(), value.path("requestTimeoutMs").asInt(),
                value.path("dataCacheTtlMs").asInt());
    }

    public MbtiSnapshot mbti() {
        ObjectNode value = current(MBTI_KEY, load(MBTI_KEY));
        return new MbtiSnapshot(secret(value), value.path("baseUrl").asText());
    }

    public SystemConfigVO getAdminConfig(String key) {
        requireKey(key);
        SystemConfigEntity row = load(key);
        ObjectNode value = current(key, row);
        boolean configured = StringUtils.hasText(value.path("apiKey").asText())
                || StringUtils.hasText(value.path("apiKeyCiphertext").asText());
        value.remove(List.of("apiKey", "apiKeyCiphertext"));
        value.put("apiKeyConfigured", configured);
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(key);
        vo.setConfigType("JSON");
        vo.setDescription(description(key));
        vo.setConfigValue(write(value));
        if (row != null && row.getUpdateTime() != null) {
            vo.setUpdateTime(row.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfigVO save(String key, String input, long operatorId) {
        requireKey(key);
        ObjectNode submitted = read(input);
        if (submitted.has("apiKeyCiphertext")) throw new IllegalArgumentException("不能直接提交接口密文");
        boolean acquired = locks.tryRun("system-config:" + key + ":save", () -> {
            SystemConfigEntity row = load(key);
            ObjectNode previous = current(key, row);
            ObjectNode stored = previous.deepCopy();
            List<String> fields = TYPESAFE_KEY.equals(key)
                    ? List.of("enabled", "model", "requestTimeoutMs", "dataCacheTtlMs") : List.of("baseUrl");
            for (String field : fields) {
                if (submitted.has(field)) stored.set(field, submitted.get(field));
            }
            validate(key, stored);
            String replacement = submitted.has("apiKey") ? text(submitted, "apiKey").trim() : "";
            boolean clear = submitted.has("clearApiKey") && bool(submitted, "clearApiKey");
            if (replacement.length() > 2048 || replacement.chars().anyMatch(Character::isWhitespace)
                    || replacement.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("API Key 格式无效");
            }
            if (clear && !replacement.isEmpty()) throw new IllegalArgumentException("清除密钥时不能同时填写新密钥");
            if (MBTI_KEY.equals(key) && !stored.path("baseUrl").asText().equals(previous.path("baseUrl").asText())
                    && replacement.isEmpty() && !clear && !secret(previous).isEmpty()) {
                throw new IllegalArgumentException("修改接口地址时请重新填写 API Key 或明确清除，避免将原密钥发送至新地址");
            }
            String apiKey = clear ? "" : replacement.isEmpty() ? secret(previous) : replacement;
            stored.remove(List.of("apiKey", "apiKeyCiphertext"));
            if (!apiKey.isEmpty()) stored.put("apiKeyCiphertext", crypto.encrypt(apiKey));
            if (row == null) {
                row = new SystemConfigEntity();
                row.setConfigKey(key);
                row.setConfigType("JSON");
                row.setDescription(description(key));
                row.setConfigValue(write(stored));
                row.fillCreateCommonField(operatorId);
                mapper.insert(row);
            } else {
                row.setConfigValue(write(stored));
                row.fillUpdateCommonField(operatorId);
                mapper.updateById(row);
            }
        });
        if (!acquired) throw new IllegalStateException("配置正在保存，请稍后重试");
        return getAdminConfig(key);
    }

    private ObjectNode current(String key, SystemConfigEntity row) {
        ObjectNode value;
        if (row != null) {
            value = read(row.getConfigValue());
            if (value.has("apiKey")) throw new IllegalStateException("接口配置包含未加密密钥，请通过后台重新保存");
        } else if (TYPESAFE_KEY.equals(key)) {
            var source = defaults.getTypesafe();
            value = json.createObjectNode().put("enabled", source.isEnabled()).put("apiKey", source.getApiKey())
                    .put("model", source.getModel()).put("requestTimeoutMs", source.getRequestTimeoutMs())
                    .put("dataCacheTtlMs", source.getDataCacheTtlMs());
        } else {
            var source = defaults.getMbti();
            value = json.createObjectNode().put("apiKey", source.getApiKey()).put("baseUrl", source.getBaseUrl());
        }
        validate(key, value);
        return value;
    }

    private void validate(String key, ObjectNode value) {
        if (TYPESAFE_KEY.equals(key)) {
            bool(value, "enabled");
            String model = text(value, "model").trim();
            if (!model.matches("[a-zA-Z0-9._:/-]{1,128}")) throw new IllegalArgumentException("模型名称格式无效");
            value.put("model", model);
            integer(value, "requestTimeoutMs", 1, 60000);
            integer(value, "dataCacheTtlMs", 0, 604800000);
        } else {
            String url = text(value, "baseUrl").trim();
            try {
                URI uri = URI.create(url);
                if (url.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("接口地址须为不含账号、查询参数或片段的 HTTPS 地址");
            }
            value.put("baseUrl", url.replaceAll("/+$", ""));
        }
    }

    private String secret(ObjectNode value) {
        String ciphertext = value.path("apiKeyCiphertext").asText("");
        return StringUtils.hasText(ciphertext) ? crypto.decrypt(ciphertext) : value.path("apiKey").asText("").trim();
    }

    private String text(ObjectNode value, String name) {
        if (!value.path(name).isTextual()) throw new IllegalArgumentException(name + " 必须是字符串");
        return value.path(name).asText();
    }

    private boolean bool(ObjectNode value, String name) {
        if (!value.path(name).isBoolean()) throw new IllegalArgumentException(name + " 必须是布尔值");
        return value.path(name).asBoolean();
    }

    private void integer(ObjectNode value, String name, int min, int max) {
        JsonNode number = value.path(name);
        if (!number.isIntegralNumber() || !number.canConvertToInt() || number.intValue() < min || number.intValue() > max) {
            throw new IllegalArgumentException(name + " 必须是 " + min + "–" + max + " 范围内的整数");
        }
    }

    private SystemConfigEntity load(String key) {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>().eq(SystemConfigEntity::getConfigKey, key));
    }

    private ObjectNode read(String input) {
        try {
            JsonNode value = json.readTree(input);
            if (!(value instanceof ObjectNode object)) throw new IllegalArgumentException();
            return object;
        } catch (Exception error) {
            throw new IllegalArgumentException("接口配置必须是有效的 JSON 对象");
        }
    }

    private String write(ObjectNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("接口配置保存失败"); }
    }

    private void requireKey(String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("不支持的接口配置项");
    }

    private String description(String key) {
        return TYPESAFE_KEY.equals(key) ? "Typesafe 智能推荐" : "MBTI 接口";
    }
}
