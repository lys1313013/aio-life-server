package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.system.mapper.ISystemConfigMapper;
import top.aiolife.system.pojo.entity.SystemConfigEntity;
import top.aiolife.system.pojo.req.SystemConfigUpdateReq;
import top.aiolife.system.service.impl.SystemConfigServiceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationSystemConfigServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ISystemConfigMapper mapper = mock(ISystemConfigMapper.class);
    private final DistributedLockExecutor locks = mock(DistributedLockExecutor.class);
    private final NotificationCryptoService crypto = new NotificationCryptoService("synthetic-test-master-key");
    private final IntegrationConfigProperties defaults = new IntegrationConfigProperties();
    private final IntegrationSystemConfigService service = new IntegrationSystemConfigService(mapper, crypto, json, locks, defaults);
    private final Map<String, SystemConfigEntity> rows = new HashMap<>();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SystemConfigEntity.class);
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<SystemConfigEntity> query = invocation.getArgument(0);
            query.getSqlSegment();
            return rows.get(query.getParamNameValuePairs().values().iterator().next());
        });
        when(mapper.insert(any(SystemConfigEntity.class))).thenAnswer(invocation -> {
            SystemConfigEntity row = invocation.getArgument(0);
            row.setId((long) rows.size() + 1);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(mapper.selectList(any())).thenAnswer(invocation -> List.copyOf(rows.values()));
        when(locks.tryRun(anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
    }

    @Test void testConfig_首次保存后以数据库为准且所有管理读取脱敏() throws Exception {
        defaults.getTypesafe().setApiKey("environment-secret");
        defaults.getTypesafe().setModel("old-model");
        var registry = new SystemConfigServiceImpl(mapper, mock(WechatSystemConfigService.class), service,
                mock(FootprintMapConfigService.class), mock(FootprintTencentConfigService.class));
        assertEquals(1, registry.list("typesafe.").size());
        assertEquals("environment-secret", service.typesafe().apiKey());
        SystemConfigUpdateReq update = new SystemConfigUpdateReq();
        update.setConfigValue("""
                {"enabled":false,"model":"new-model","requestTimeoutMs":2300,"dataCacheTtlMs":0,"apiKey":""}
                """);
        registry.update(IntegrationSystemConfigService.TYPESAFE_KEY, update, 9L);
        var row = rows.get(IntegrationSystemConfigService.TYPESAFE_KEY);
        assertEquals(9L, row.getCreateUser());
        assertFalse(row.getConfigValue().contains("environment-secret"));
        assertFalse(json.readTree(row.getConfigValue()).has("apiKey"));
        assertEquals("environment-secret", crypto.decrypt(json.readTree(row.getConfigValue()).get("apiKeyCiphertext").asText()));
        defaults.getTypesafe().setApiKey("changed-environment");
        defaults.getTypesafe().setModel("changed-environment");
        var snapshot = service.typesafe();
        assertEquals("environment-secret", snapshot.apiKey());
        assertEquals("new-model", snapshot.model());
        assertEquals(2300, snapshot.requestTimeoutMs());
        assertEquals(0, snapshot.dataCacheTtlMs());
        assertFalse(snapshot.enabled());
        for (String value : List.of(registry.getValueByKey(IntegrationSystemConfigService.TYPESAFE_KEY),
                registry.getByKey(IntegrationSystemConfigService.TYPESAFE_KEY).getConfigValue(),
                registry.list("typesafe.").getFirst().getConfigValue())) {
            assertFalse(value.contains("secret"));
            assertFalse(value.contains("Ciphertext"));
            assertTrue(json.readTree(value).get("apiKeyConfigured").asBoolean());
        }
    }

    @Test void testSecret_替换留空和明确清除立即生效且不回退环境变量() {
        for (String key : IntegrationSystemConfigService.KEYS) {
            service.save(key, "{\"apiKey\":\"first-secret\"}", 9L);
            service.save(key, "{\"apiKey\":\"\"}", 10L);
            assertEquals("first-secret", secret(key));
            service.save(key, "{\"apiKey\":\"replacement-secret\"}", 10L);
            assertEquals("replacement-secret", secret(key));
            service.save(key, "{\"clearApiKey\":true}", 10L);
            assertEquals("", secret(key));
            assertEquals(10L, rows.get(key).getUpdateUser());
            assertFalse(rows.get(key).getConfigValue().contains("clearApiKey"));
            assertFalse(rows.get(key).getConfigValue().contains("Ciphertext"));
        }
    }

    @Test void testSave_拒绝非法参数与密文注入() {
        for (String input : List.of("null", "[]", "not-json", "{\"apiKeyCiphertext\":null}",
                "{\"apiKey\":\"a b\"}", "{\"apiKey\":\"secret\",\"clearApiKey\":true}",
                "{\"enabled\":\"false\"}", "{\"requestTimeoutMs\":0}", "{\"requestTimeoutMs\":60001}",
                "{\"requestTimeoutMs\":1.5}", "{\"requestTimeoutMs\":\"1000\"}",
                "{\"dataCacheTtlMs\":-1}", "{\"dataCacheTtlMs\":604800001}", "{\"model\":\"\"}")) {
            assertThrows(IllegalArgumentException.class, () -> service.save(IntegrationSystemConfigService.TYPESAFE_KEY, input, 9L), input);
        }
        for (String url : List.of("http://example.com", "https://user:pass@example.com", "https://example.com?a=b", "https://example.com/#x", "file:///tmp/key")) {
            assertThrows(IllegalArgumentException.class, () -> service.save(IntegrationSystemConfigService.MBTI_KEY,
                    "{\"baseUrl\":\"" + url + "\"}", 9L));
        }
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
    }

    @Test void testSave_更换地址需确认密钥且锁失败保留配置() {
        service.save(IntegrationSystemConfigService.MBTI_KEY, "{\"apiKey\":\"old-secret\"}", 9L);
        assertThrows(IllegalArgumentException.class, () -> service.save(IntegrationSystemConfigService.MBTI_KEY,
                "{\"baseUrl\":\"https://new.example.com\"}", 9L));
        assertEquals("https://api.devil.ai", service.mbti().baseUrl());
        service.save(IntegrationSystemConfigService.MBTI_KEY,
                "{\"baseUrl\":\"https://new.example.com/\",\"apiKey\":\"new-secret\"}", 9L);
        assertEquals("https://new.example.com", service.mbti().baseUrl());
        assertEquals("new-secret", service.mbti().apiKey());
        doReturn(false).when(locks).tryRun(anyString(), any());
        assertThrows(IllegalStateException.class, () -> service.save(IntegrationSystemConfigService.MBTI_KEY, "{\"clearApiKey\":true}", 9L));
        assertEquals("new-secret", service.mbti().apiKey());
    }

    @Test void testSave_缺少加密主密钥拒绝写入() {
        var invalid = new IntegrationSystemConfigService(mapper, new NotificationCryptoService(""), json, locks, defaults);
        assertThrows(IllegalStateException.class, () -> invalid.save(IntegrationSystemConfigService.MBTI_KEY, "{\"apiKey\":\"secret\"}", 9L));
        verify(mapper, never()).insert(any(SystemConfigEntity.class));
    }

    private String secret(String key) {
        return IntegrationSystemConfigService.TYPESAFE_KEY.equals(key) ? service.typesafe().apiKey() : service.mbti().apiKey();
    }
}
