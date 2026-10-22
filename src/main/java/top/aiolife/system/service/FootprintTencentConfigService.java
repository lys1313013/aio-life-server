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

/** Web 腾讯地图展示配置；Key 加密入库，管理员读取不回显。 */
@Service
@RequiredArgsConstructor
public class FootprintTencentConfigService {
    public static final String KEY = "footprints.tencent";
    private final ISystemConfigMapper mapper;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;

    public record BrowserConfig(String key, String mapStyleId) {}

    @Getter @Setter @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        private String browserKey;
        private String browserKeyCiphertext;
        private boolean clearBrowserKey;
        private String mapStyleId = "style1";
    }

    /** 浏览器 Key 按腾讯协议在登录后的 SDK 请求中公开；不包含百度服务端 AK。 */
    public BrowserConfig browserConfig() {
        Value value = stored(load());
        return new BrowserConfig(StringUtils.hasText(value.getBrowserKeyCiphertext())
                ? crypto.decrypt(value.getBrowserKeyCiphertext()) : "", value.getMapStyleId());
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
            if (value.getBrowserKeyCiphertext() != null) throw new IllegalArgumentException("不能直接提交地图密文");
            String key = value.getBrowserKey() == null ? "" : value.getBrowserKey().trim();
            if (!key.isEmpty() && !key.matches("[A-Za-z0-9_-]{10,256}")) {
                throw new IllegalArgumentException("腾讯地图 Key 格式无效");
            }
            String style = value.getMapStyleId() == null ? "" : value.getMapStyleId().trim();
            if (!style.matches("style[1-9][0-9]{0,5}")) throw new IllegalArgumentException("样式编号须为 style1 等格式");
            if (value.isClearBrowserKey() && !key.isEmpty()) throw new IllegalArgumentException("清除 Key 时不能同时填写新 Key");
            SystemConfigEntity row = load();
            String ciphertext = stored(row).getBrowserKeyCiphertext();
            if (value.isClearBrowserKey()) ciphertext = null;
            else if (!key.isEmpty()) ciphertext = crypto.encrypt(key);
            var saved = json.createObjectNode().put("mapStyleId", style);
            if (ciphertext != null) saved.put("browserKeyCiphertext", ciphertext);
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

    private SystemConfigEntity load() {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>().eq(SystemConfigEntity::getConfigKey, KEY));
    }
    private Value stored(SystemConfigEntity row) {
        Value value = row == null ? new Value() : read(row.getConfigValue());
        if (StringUtils.hasText(value.getBrowserKey())) throw new IllegalStateException("地图配置包含未加密 Key，请重新保存");
        return value;
    }
    private Value read(String input) {
        try {
            Value value = json.readValue(input, Value.class);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (Exception exception) { throw new IllegalArgumentException("腾讯地图配置必须为有效的配置对象"); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("地图配置保存失败"); }
    }
}
