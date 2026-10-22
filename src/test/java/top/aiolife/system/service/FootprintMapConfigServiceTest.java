package top.aiolife.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.req.SystemConfigUpdateReq;
import top.aiolife.system.service.impl.SystemConfigServiceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FootprintMapConfigServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ISystemConfigMapper mapper = mock(ISystemConfigMapper.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final NotificationCryptoService crypto = new NotificationCryptoService("synthetic-test-master-key");
    private final FootprintMapConfigService service = new FootprintMapConfigService(mapper, crypto, json, locks);
    private SystemConfigEntity row;

    @BeforeEach void setup() {
        when(mapper.selectOne(any())).thenAnswer(invocation -> row);
        when(mapper.insert(any(SystemConfigEntity.class))).thenAnswer(invocation -> {
            row = invocation.getArgument(0);
            row.setId(7L);
            return 1;
        });
        when(locks.tryRun(anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
    }

    @Test void testSave_首次可发现配置且加密保存不回显服务端AK() throws Exception {
        assertEquals("footprints.baidu", service.getAdminConfig().getConfigKey());
        assertFalse(service.searchEnabled());
        var result = service.save("""
                {"browserAk":" synthetic-browser ","serviceAk":" synthetic-service "}
                """, 9L);
        assertEquals(9L, row.getCreateUser());
        var stored = json.readTree(row.getConfigValue());
        assertFalse(stored.has("serviceAk"));
        assertFalse(stored.has("clearServiceAk"));
        assertFalse(row.getConfigValue().contains("synthetic-service"));
        assertEquals("synthetic-service", crypto.decrypt(stored.get("serviceAkCiphertext").asText()));
        assertEquals("synthetic-service", service.serviceKey());
        assertEquals(FootprintMapConfigService.DEFAULT_TILE_URL, service.tileSource().url());
        assertEquals("WGS84", service.tileSource().coordinateSystem());
        assertFalse(result.getConfigValue().contains("browserAk"));
        assertTrue(service.searchEnabled());
        assertFalse(result.getConfigValue().contains("synthetic-service"));
        assertFalse(result.getConfigValue().contains("Ciphertext"));
        assertTrue(json.readTree(result.getConfigValue()).get("serviceAkConfigured").asBoolean());
    }

    @Test void testSave_留空保留替换和显式清除即时生效() {
        service.save("{\"browserAk\":\"browser\",\"serviceAk\":\"first-secret\"}", 9L);
        service.save("{\"browserAk\":\"new-browser\",\"serviceAk\":\"\"}", 10L);
        assertEquals("first-secret", service.serviceKey());
        assertEquals(10L, row.getUpdateUser());
        service.save("{\"browserAk\":\"new-browser\",\"serviceAk\":\"replacement-secret\"}", 10L);
        assertEquals("replacement-secret", service.serviceKey());
        service.save("{\"browserAk\":\"\",\"clearServiceAk\":true}", 10L);
        assertEquals("", service.serviceKey());
        assertFalse(service.searchEnabled());
        verify(mapper, times(1)).insert(any(SystemConfigEntity.class));
        verify(mapper, times(3)).updateById(any(SystemConfigEntity.class));
    }

    @Test void testSave_拒绝无效输入密文注入和相互冲突指令() {
        for (String input : List.of("null", "[]", "not-json",
                "{\"serviceAkCiphertext\":\"v1:untrusted\"}",
                "{\"serviceAk\":\"secret\",\"clearServiceAk\":true}",
                "{\"serviceAk\":\"" + "a".repeat(257) + "\"}")) {
            assertThrows(IllegalArgumentException.class, () -> service.save(input, 9L));
        }
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
    }

    @Test void testSave_未配置加密主密钥拒绝写入且锁占用不覆盖配置() {
        var unconfigured = new FootprintMapConfigService(mapper, new NotificationCryptoService(""), json, locks);
        assertThrows(IllegalStateException.class, () -> unconfigured.save("{\"serviceAk\":\"secret\"}", 9L));
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
        doReturn(false).when(locks).tryRun(anyString(), any());
        assertThrows(IllegalStateException.class, () -> service.save("{}", 9L));
    }

    @Test void testSystemConfig_列表单项及通用读取均不泄露密文且可通过管理接口保存() {
        var registry = new SystemConfigServiceImpl(mapper, mock(WechatSystemConfigService.class), service, mock(FootprintTencentConfigService.class));
        var request = new SystemConfigUpdateReq();
        request.setConfigValue("{\"browserAk\":\"browser\",\"serviceAk\":\"secret\"}");
        registry.update(FootprintMapConfigService.KEY, request, 9L);
        when(mapper.selectList(any())).thenReturn(List.of(row));
        var configs = registry.list("footprints.baidu");
        assertEquals(1, configs.size());
        for (String value : List.of(configs.getFirst().getConfigValue(),
                registry.getByKey(FootprintMapConfigService.KEY).getConfigValue(),
                registry.getValueByKey(FootprintMapConfigService.KEY))) {
            assertFalse(value.contains("secret"));
            assertFalse(value.contains("Ciphertext"));
        }
        assertTrue(registry.list("other.").isEmpty());
    }

    @Test void testTiles_底图可独立配置无需AK且移除旧浏览器配置() throws Exception {
        service.save("{}", 9L);
        var previous = json.readTree(row.getConfigValue());
        ((ObjectNode) previous).put("browserAk", "synthetic-legacy-browser");
        row.setConfigValue(json.writeValueAsString(previous));
        service.save("""
                {"tileUrl":"/maps/{z}/{x}/{y}.png","tileAttribution":"自建底图", "tileAttributionUrl":"", "tileCoordinateSystem":"GCJ02"}
                """, 9L);
        assertEquals("/maps/{z}/{x}/{y}.png", service.tileSource().url());
        assertEquals("GCJ02", service.tileSource().coordinateSystem());
        assertEquals("自建底图", service.tileSource().attribution());
        assertFalse(service.searchEnabled());
        assertFalse(row.getConfigValue().contains("browserAk"));
        assertFalse(service.getAdminConfig().getConfigValue().contains("legacy-browser"));
    }

    @Test void testTiles_拒绝无效模板不安全URL和错误坐标系() {
        for (String input : List.of(
                "{\"tileUrl\":\"https://tiles.example.com/map.png\"}",
                "{\"tileUrl\":\"http://tiles.example.com/{z}/{x}/{y}.png\"}",
                "{\"tileUrl\":\"https://user:password@tiles.example.com/{z}/{x}/{y}.png\"}",
                "{\"tileUrl\":\"//tiles.example.com/{z}/{x}/{y}.png\"}",
                "{\"tileUrl\":\"/maps/{token}/{z}/{x}/{y}.png\"}",
                "{\"tileUrl\":\"/maps/invalid path/{z}/{x}/{y}.png\"}",
                "{\"tileAttributionUrl\":\"javascript:alert(1)\"}",
                "{\"tileAttribution\":\"\"}",
                "{\"tileCoordinateSystem\":\"BD09\"}")) {
            assertThrows(IllegalArgumentException.class, () -> service.save(input, 9L));
        }
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
    }
}
