package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.config.FootprintMapProperties;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.system.mapper.ISystemConfigMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationConfigPersistenceTest {
    @Test void testPersistence_独立读取实例即时读取更新且密钥仅保存密文() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:integration_config;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE system_config (
                    id BIGINT PRIMARY KEY, config_key VARCHAR(100) NOT NULL UNIQUE,
                    config_value TEXT, config_type VARCHAR(20), description VARCHAR(255),
                    is_deleted INT DEFAULT 0, create_user BIGINT, update_user BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        var configuration = new MybatisConfiguration();
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(ISystemConfigMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var locks = mock(DistributedLockExecutor.class);
        when(locks.tryRun(anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run(); return true;
        });
        var crypto = new NotificationCryptoService("synthetic-test-master-key");
        var json = new ObjectMapper();
        var defaults = new IntegrationConfigProperties();
        try (var writerSession = factory.getObject().openSession(true);
             var readerSession = factory.getObject().openSession(true)) {
            var writer = new IntegrationSystemConfigService(writerSession.getMapper(ISystemConfigMapper.class), crypto, json, locks, defaults);
            var reader = new IntegrationSystemConfigService(readerSession.getMapper(ISystemConfigMapper.class), crypto, json, locks, defaults);
            assertEquals("jev-latest", reader.typesafe().model());
            writer.save(IntegrationSystemConfigService.TYPESAFE_KEY,
                    "{\"apiKey\":\"synthetic-secret\",\"model\":\"new-model\",\"requestTimeoutMs\":2500,\"dataCacheTtlMs\":0}", 7L);
            assertEquals("new-model", reader.typesafe().model());
            assertEquals("synthetic-secret", reader.typesafe().apiKey());
            assertEquals(2500, reader.typesafe().requestTimeoutMs());
            assertEquals(0, reader.typesafe().dataCacheTtlMs());
            String stored = jdbc.queryForObject("SELECT config_value FROM system_config WHERE config_key = ?", String.class,
                    IntegrationSystemConfigService.TYPESAFE_KEY);
            assertFalse(stored.contains("synthetic-secret"));
            assertTrue(stored.contains("apiKeyCiphertext"));
            writer.save(IntegrationSystemConfigService.TYPESAFE_KEY, "{\"enabled\":false,\"clearApiKey\":true}", 8L);
            assertFalse(reader.typesafe().enabled());
            assertEquals("", reader.typesafe().apiKey());
            writer.save(IntegrationSystemConfigService.MBTI_KEY, "{\"apiKey\":\"mbti-secret\",\"baseUrl\":\"https://api.example.com\"}", 8L);
            assertEquals("https://api.example.com", reader.mbti().baseUrl());
            assertEquals("mbti-secret", reader.mbti().apiKey());
            var mapWriter = new FootprintMapConfigService(writerSession.getMapper(ISystemConfigMapper.class), crypto, json, locks, new FootprintMapProperties());
            var mapReader = new FootprintMapConfigService(readerSession.getMapper(ISystemConfigMapper.class), crypto, json, locks, new FootprintMapProperties());
            assertEquals(5000, mapReader.timeoutMs());
            mapWriter.save("{\"timeoutMs\":1500}", 7L);
            assertEquals(1500, mapReader.timeoutMs());
            mapWriter.save("{\"timeoutMs\":8000}", 8L);
            assertEquals(8000, mapReader.timeoutMs());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM system_config", Integer.class));
        }
    }
}
