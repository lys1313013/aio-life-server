package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.vo.SystemConfigVO;

/** Web 腾讯地图代理配置；Key 加密入库，仅服务器转发时解密。 */
@Service
@RequiredArgsConstructor
public class FootprintTencentConfigService {
    public static final String KEY = "footprints.tencent";
    private final ISystemConfigMapper mapper;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;

    public record BrowserConfig(boolean enabled, String mapStyleId) {}

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        private String browserKey;
        private String browserKeyCiphertext;
        private boolean clearBrowserKey;
        private String serviceKey;
        private String serviceKeyCiphertext;
        private boolean clearServiceKey;
        private Integer timeoutMs;
        private String mapStyleId = "style1";
    }

    /** 浏览器只获得可用状态与样式，不能获得 Key 或密文。 */
    public BrowserConfig browserConfig() {
        Value value = stored(load());
        return new BrowserConfig(StringUtils.hasText(value.getBrowserKeyCiphertext()), value.getMapStyleId());
    }

    /** 仅供固定上游的腾讯代理调用；沿用历史加密配置，无需重新填写 Key。 */
    public String displayKey() {
        Value value = stored(load());
        return StringUtils.hasText(value.getBrowserKeyCiphertext())
                ? crypto.decrypt(value.getBrowserKeyCiphertext()) : "";
    }

    /** 搜索、正向与逆向地址解析专用，不回退到展示 Key。 */
    public String serviceKey() {
        String ciphertext = stored(load()).getServiceKeyCiphertext();
        return StringUtils.hasText(ciphertext) ? crypto.decrypt(ciphertext) : "";
    }

    public boolean searchEnabled() {
        return StringUtils.hasText(stored(load()).getServiceKeyCiphertext());
    }

    public int timeoutMs() {
        Integer value = stored(load()).getTimeoutMs();
        return value == null ? 5000 : value;
    }

    public SystemConfigVO getAdminConfig() {
        SystemConfigEntity row = load();
        Value value = stored(row);
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(KEY);
        vo.setConfigType("JSON");
        vo.setDescription("生活足迹腾讯地图（Web）");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("browserKeyConfigured", StringUtils.hasText(value.getBrowserKeyCiphertext()));
        form.put("mapStyleId", value.getMapStyleId());
        form.put("serviceKeyConfigured", StringUtils.hasText(value.getServiceKeyCiphertext()));
        form.put("timeoutMs", value.getTimeoutMs() == null ? 5000 : value.getTimeoutMs());
        vo.setConfigValue(write(form));
        if (row != null && row.getUpdateTime() != null) {
            vo.setUpdateTime(row.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfigVO save(String input, long operatorId) {
        boolean acquired = locks.tryRun("system-config:footprints-tencent:save", () -> {
            Value value = read(input);
            if (value.getBrowserKeyCiphertext() != null || value.getServiceKeyCiphertext() != null)
                throw new IllegalArgumentException("不能直接提交地图密文");
            String key = validateKey(value.getBrowserKey(), value.isClearBrowserKey());
            String serviceKey = validateKey(value.getServiceKey(), value.isClearServiceKey());
            String style = value.getMapStyleId() == null ? "" : value.getMapStyleId().trim();
            if (!style.matches("style[1-9][0-9]{0,5}")) throw new IllegalArgumentException("样式编号须为 style1 等格式");
            SystemConfigEntity row = load();
            Value previous = stored(row);
            String ciphertext = updateKey(previous.getBrowserKeyCiphertext(), key, value.isClearBrowserKey());
            String serviceCiphertext = updateKey(previous.getServiceKeyCiphertext(), serviceKey, value.isClearServiceKey());
            if (StringUtils.hasText(ciphertext) && StringUtils.hasText(serviceCiphertext)
                    && crypto.decrypt(ciphertext).equals(crypto.decrypt(serviceCiphertext))) {
                throw new IllegalArgumentException("地图展示和搜索解析必须使用不同的 Key");
            }
            Integer timeout = value.getTimeoutMs() == null ? previous.getTimeoutMs() : value.getTimeoutMs();
            var saved = json.createObjectNode().put("mapStyleId", style).put("timeoutMs", timeout == null ? 5000 : timeout);
            if (ciphertext != null) saved.put("browserKeyCiphertext", ciphertext);
            if (serviceCiphertext != null) saved.put("serviceKeyCiphertext", serviceCiphertext);
            if (row == null) {
                row = new SystemConfigEntity();
                row.setConfigKey(KEY);
                row.setConfigType("JSON");
                row.setDescription("生活足迹腾讯地图（Web）");
                row.setConfigValue(write(saved));
                row.fillCreateCommonField(operatorId);
                mapper.insert(row);
            } else {
                row.setConfigValue(write(saved));
                row.fillUpdateCommonField(operatorId);
                mapper.updateById(row);
            }
        });
        if (!acquired) throw new IllegalStateException("配置正在保存，请稍后重试");
        return getAdminConfig();
    }

    private String validateKey(String input, boolean clear) {
        String key = input == null ? "" : input.trim();
        if (!key.isEmpty() && !key.matches("[A-Za-z0-9_-]{10,256}")) throw new IllegalArgumentException("腾讯地图 Key 格式无效");
        if (clear && !key.isEmpty()) throw new IllegalArgumentException("清除 Key 时不能同时填写新 Key");
        return key;
    }
    private String updateKey(String previous, String key, boolean clear) {
        return clear ? null : key.isEmpty() ? previous : crypto.encrypt(key);
    }

    private SystemConfigEntity load() {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>().eq(SystemConfigEntity::getConfigKey, KEY));
    }
    private Value stored(SystemConfigEntity row) {
        Value value = row == null ? new Value() : read(row.getConfigValue());
        if (StringUtils.hasText(value.getBrowserKey()) || StringUtils.hasText(value.getServiceKey())) throw new IllegalStateException("地图配置包含未加密 Key，请重新保存");
        return value;
    }
    private Value read(String input) {
        try {
            var node = json.readTree(input);
            if (node != null && node.has("timeoutMs")) {
                var timeout = node.get("timeoutMs");
                if (!timeout.isIntegralNumber() || !timeout.canConvertToInt()
                        || timeout.intValue() < 1000 || timeout.intValue() > 15000) throw new IllegalArgumentException();
            }
            Value value = json.treeToValue(node, Value.class);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (Exception exception) { throw new IllegalArgumentException("腾讯地图配置必须为有效的配置对象"); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("地图配置保存失败"); }
    }
}
