package top.aiolife.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class FootprintTencentConfigServiceTest {
    private final ISystemConfigMapper mapper = mock(ISystemConfigMapper.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final ObjectMapper json = new ObjectMapper();
    private final FootprintTencentConfigService service = new FootprintTencentConfigService(mapper,
            new NotificationCryptoService("synthetic-test-master"), json, locks);
    private SystemConfigEntity row;
    @BeforeEach void setup() {
        when(mapper.selectOne(any())).thenAnswer(i -> row);
        when(mapper.insert(any(SystemConfigEntity.class))).thenAnswer(i -> { row = i.getArgument(0); row.setId(8L); return 1; });
        when(locks.tryRun(anyString(), any())).thenAnswer(i -> { i.<Runnable>getArgument(1).run(); return true; });
    }
    @Test void testKey_加密保存脱敏读取且按需提供浏览器配置() {
        assertEquals("", service.browserConfig().key());
        service.save("{\"browserKey\":\"synthetic-tencent-key\",\"mapStyleId\":\"style1\"}", 9L);
        assertFalse(row.getConfigValue().contains("synthetic-tencent-key"));
        assertEquals("synthetic-tencent-key", service.browserConfig().key());
        assertEquals("style1", service.browserConfig().mapStyleId());
        assertFalse(service.getAdminConfig().getConfigValue().contains("synthetic-tencent-key"));
        assertFalse(service.getAdminConfig().getConfigValue().contains("Ciphertext"));
        service.save("{\"browserKey\":\"\",\"mapStyleId\":\"style2\"}", 10L);
        assertEquals("synthetic-tencent-key", service.browserConfig().key());
        assertEquals("style2", service.browserConfig().mapStyleId());
        service.save("{\"clearBrowserKey\":true}", 10L);
        assertEquals("", service.browserConfig().key());
        verify(mapper, times(1)).insert(any(SystemConfigEntity.class));
    }
    @Test void testRegistry_通用配置入口均脱敏且管理保存可发现配置() {
        var registry = new SystemConfigServiceImpl(mapper, mock(WechatSystemConfigService.class), mock(IntegrationSystemConfigService.class), mock(FootprintMapConfigService.class), service);
        var req = new SystemConfigUpdateReq(); req.setConfigValue("{\"browserKey\":\"synthetic-tencent-key\"}");
        registry.update(FootprintTencentConfigService.KEY, req, 9L);
        when(mapper.selectList(any())).thenReturn(List.of(row));
        assertEquals(1, registry.list("footprints.tencent").size());
        for (String value : List.of(registry.list("footprints.tencent").getFirst().getConfigValue(),
                registry.getByKey(FootprintTencentConfigService.KEY).getConfigValue(), registry.getValueByKey(FootprintTencentConfigService.KEY))) {
            assertFalse(value.contains("synthetic-tencent-key")); assertFalse(value.contains("Ciphertext"));
        }
    }
    @Test void testValidation_拒绝密文注入冲突清除以及无效样式() {
        for (String value : List.of("null", "[]", "{\"browserKeyCiphertext\":\"unsafe\"}",
                "{\"browserKey\":\"bad key\"}", "{\"browserKey\":\"synthetic-key\",\"clearBrowserKey\":true}",
                "{\"mapStyleId\":\"https://untrusted.test\"}")) {
            assertThrows(IllegalArgumentException.class, () -> service.save(value, 9L));
        }
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
    }
}
