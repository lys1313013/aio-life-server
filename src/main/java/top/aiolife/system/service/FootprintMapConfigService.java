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
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.vo.SystemConfigVO;

import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 地图 AK 从系统配置实时读取；服务端 AK 加密保存且不回显。 */
@Service
@RequiredArgsConstructor
public class FootprintMapConfigService {
    public static final String KEY = "footprints.baidu";
    public static final String DEFAULT_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    public record TileSource(String url, String attribution, String attributionUrl, String coordinateSystem) {}
    private final ISystemConfigMapper mapper;
    private final NotificationCryptoService crypto;
    private final ObjectMapper json;
    private final DistributedLockExecutor locks;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        private String serviceAk;
        private String serviceAkCiphertext;
        private boolean clearServiceAk;
        private String tileUrl = DEFAULT_TILE_URL;
        private String tileAttribution = "© OpenStreetMap contributors";
        private String tileAttributionUrl = "https://www.openstreetmap.org/copyright";
        private String tileCoordinateSystem = "WGS84";
    }

    public boolean searchEnabled() {
        return StringUtils.hasText(current().getServiceAkCiphertext());
    }

    public TileSource tileSource() {
        Value value = current();
        return new TileSource(value.getTileUrl(), value.getTileAttribution(),
                value.getTileAttributionUrl(), value.getTileCoordinateSystem());
    }

    public String serviceKey() {
        String ciphertext = current().getServiceAkCiphertext();
        return StringUtils.hasText(ciphertext) ? crypto.decrypt(ciphertext) : "";
    }

    public SystemConfigVO getAdminConfig() {
        SystemConfigEntity row = load();
        Value value = row == null ? new Value() : readStored(row.getConfigValue());
        SystemConfigVO vo = new SystemConfigVO();
        vo.setConfigKey(KEY);
        vo.setConfigType("JSON");
        vo.setDescription("生活足迹地图");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("serviceAkConfigured", StringUtils.hasText(value.getServiceAkCiphertext()));
        form.put("tileUrl", value.getTileUrl());
        form.put("tileAttribution", value.getTileAttribution());
        form.put("tileAttributionUrl", value.getTileAttributionUrl());
        form.put("tileCoordinateSystem", value.getTileCoordinateSystem());
        vo.setConfigValue(write(form));
        if (row != null && row.getUpdateTime() != null) {
            vo.setUpdateTime(row.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfigVO save(String input, long operatorId) {
        boolean acquired = locks.tryRun("system-config:footprints-baidu:save", () -> {
            Value value = read(input);
            if (value.getServiceAkCiphertext() != null) throw new IllegalArgumentException("不能直接提交地图密文");
            validateTiles(value);
            String secret = validateKey(value.getServiceAk());
            if (value.isClearServiceAk() && !secret.isEmpty()) {
                throw new IllegalArgumentException("清除服务端 AK 时不能同时填写新 AK");
            }
            SystemConfigEntity row = load();
            Value previous = row == null ? new Value() : readStored(row.getConfigValue());
            String ciphertext = previous.getServiceAkCiphertext();
            if (value.isClearServiceAk()) ciphertext = null;
            else if (!secret.isEmpty()) ciphertext = crypto.encrypt(secret);
            // 只存允许的字段，明文 AK 和清除指令均不落库。
            var stored = json.createObjectNode()
                    .put("tileUrl", value.getTileUrl())
                    .put("tileAttribution", value.getTileAttribution())
                    .put("tileAttributionUrl", value.getTileAttributionUrl())
                    .put("tileCoordinateSystem", value.getTileCoordinateSystem());
            if (ciphertext != null) stored.put("serviceAkCiphertext", ciphertext);
            if (row == null) {
                row = new SystemConfigEntity();
                row.setConfigKey(KEY);
                row.setConfigType("JSON");
                row.setDescription("生活足迹地图");
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
        return getAdminConfig();
    }

    private Value current() {
        SystemConfigEntity row = load();
        return row == null ? new Value() : readStored(row.getConfigValue());
    }

    private SystemConfigEntity load() {
        return mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>().eq(SystemConfigEntity::getConfigKey, KEY));
    }

    private Value readStored(String input) {
        Value value = read(input);
        if (StringUtils.hasText(value.getServiceAk())) {
            throw new IllegalStateException("地图配置包含未加密 AK，请通过系统配置重新保存");
        }
        validateTiles(value);
        return value;
    }

    private Value read(String input) {
        try {
            Value value = json.readValue(input, Value.class);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("地图配置必须是有效的配置对象");
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("地图配置保存失败"); }
    }

    private String validateKey(String input) {
        String value = input == null ? "" : input.trim();
        if (!value.isEmpty() && !value.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException("AK 格式无效，请填写百度控制台中的完整 AK");
        }
        return value;
    }

    private void validateTiles(Value value) {
        String url = trim(value.getTileUrl());
        if (url.isEmpty()) url = DEFAULT_TILE_URL;
        if (url.length() > 2048 || !url.contains("{z}") || !url.contains("{x}") || !url.contains("{y}")) {
            throw new IllegalArgumentException("底图地址须包含 {z}、{x}、{y}");
        }
        String sample = url.replaceAll("\\{[zxysr]}", "0");
        if (sample.contains("{") || sample.contains("}") || sample.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("底图地址包含不支持的占位符或空白字符");
        }
        if (!(url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")) && !httpsUrl(sample)) {
            throw new IllegalArgumentException("底图地址须使用 HTTPS 或本站相对路径");
        }
        String attribution = trim(value.getTileAttribution());
        String attributionUrl = trim(value.getTileAttributionUrl());
        if (attribution.isEmpty() || attribution.length() > 200 || attributionUrl.length() > 500
                || (!attributionUrl.isEmpty() && !httpsUrl(attributionUrl))) {
            throw new IllegalArgumentException("请填写底图署名，署名链接须使用 HTTPS 或留空");
        }
        if (value.getTileCoordinateSystem() == null || !Set.of("WGS84", "GCJ02").contains(value.getTileCoordinateSystem())) {
            throw new IllegalArgumentException("底图坐标系须选择 WGS84 或 GCJ02");
        }
        value.setTileUrl(url);
        value.setTileAttribution(attribution);
        value.setTileAttributionUrl(attributionUrl);
    }

    private boolean httpsUrl(String input) {
        try {
            URI uri = URI.create(input);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) { return false; }
    }

    private String trim(String input) { return input == null ? "" : input.trim(); }
}
